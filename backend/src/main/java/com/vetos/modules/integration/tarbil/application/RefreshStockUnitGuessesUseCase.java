package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * TARBİL stoğu gelince Vetly'de eşleşen her ürüne takdim şeklinden birim tahmini uygulanır (değerlendirme I4): miktarı
 * aynı olan ürün eşitleme ekranında seçilemediği için başka yoldan tahmin almaz; eski ml'li ürün "adet" kalır ve eklenti
 * TARBİL'e yanlış Miktar yazardı. Miktarlara dokunmaz; elle ayarlanmış birim ve bekleyen düşümü olan ürün atlanır (port).
 */
@Service
@RequiredArgsConstructor
public class RefreshStockUnitGuessesUseCase {

    private final TarbilStockSnapshotRepository repository;
    private final StaffUserLookupPort staffUserLookupPort;
    private final TarbilStockSyncPort stockSyncPort;

    public void execute(UUID staffUserId, UUID snapshotId) {
        TarbilStockSnapshot snapshot = repository.findById(snapshotId).orElse(null);
        UUID branchId = staffUserLookupPort.findSummaryById(staffUserId).branchId();
        if (snapshot == null || branchId == null) {
            return;
        }
        List<TarbilStockSnapshotLine> lines = repository.findLines(snapshotId);
        Map<String, StockSyncMatcher.Target> targets = StockSyncMatcher.targets(snapshot.getTarbilSystem(), snapshot.isPartStockRead(), lines);
        List<InventoryStockView> stock = stockSyncPort.listForBranch(branchId);
        Set<UUID> done = new HashSet<>();
        for (TarbilStockSnapshotLine line : lines) {
            StockSyncMatcher.Target target = targets.get(StockSyncMatcher.key(line));
            InventoryStockView item = StockSyncMatcher.match(line, stock, target).item();
            if (item != null && done.add(item.id())) {
                stockSyncPort.refreshUnitGuess(item.id(), snapshot.getTarbilSystem().name(), line.getPresentation(), target.dosesPerPackage());
            }
        }
    }
}
