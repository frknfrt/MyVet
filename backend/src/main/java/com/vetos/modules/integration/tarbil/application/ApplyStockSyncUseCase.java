package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilStockSnapshotNotFoundException;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.TarbilStockLink;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hekimin sectigi goruntu satirlarini Vetly stoguna isler (yeni kalem ya da miktar esitleme). Islenmis satir tekrar
 * islenmez. Goruntu satiri kilitlenir: ayni goruntuyu ayni anda isleyen ikinci istek ilkinin bitmesini bekler ve
 * satirlari islenmis gorur (cift sekme / yeniden deneme stogu iki katina cikarmasin).
 */
@Service
@RequiredArgsConstructor
public class ApplyStockSyncUseCase {

    private final TarbilStockSnapshotRepository repository;
    private final TarbilStockSyncPort stockSyncPort;

    @Transactional
    public int execute(UUID tenantId, UUID branchId, UUID snapshotId, List<UUID> lineIds) {
        TarbilStockSnapshot snapshot = repository.findByIdForUpdate(snapshotId)
            .filter(s -> s.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilStockSnapshotNotFoundException(snapshotId));
        Set<UUID> wanted = new HashSet<>(lineIds == null ? List.of() : lineIds);
        String system = snapshot.getTarbilSystem().name();
        String category = snapshot.getTarbilSystem() == TarbilStockSystem.HBSAPP_VACCINE ? "Aşı" : "İlaç";
        List<TarbilStockSnapshotLine> lines = repository.findLines(snapshotId);
        Map<String, Integer> totals = StockSyncMatcher.totals(lines);
        List<InventoryStockView> stock = stockSyncPort.listForBranch(branchId);
        Instant now = Instant.now();
        int applied = 0;
        for (TarbilStockSnapshotLine line : lines) {
            if (!wanted.contains(line.getId()) || line.isApplied()) {
                continue;
            }
            String key = StockSyncMatcher.key(line);
            int target = totals.get(key);
            StockSyncMatcher.Match match = StockSyncMatcher.match(line, stock, target);
            UUID itemId;
            if (match.status() == TarbilStockSyncStatus.NEW) {
                itemId = stockSyncPort.createFromTarbil(branchId, new NewTarbilStockItem(system, line.getProductName(),
                    line.getPresentation(), category, line.getLotNumber(), line.getExpiryDate(), target), snapshotId);
            } else {
                itemId = match.item().id();
                stockSyncPort.syncFromTarbil(itemId, target,
                    new TarbilStockLink(system, line.getProductName(), line.getPresentation()), snapshotId);
            }
            // Ayni urun+lotun diger satirlari ayni kaleme islendi: tekrar islenmesinler.
            for (TarbilStockSnapshotLine sibling : lines) {
                if (!sibling.isApplied() && key.equals(StockSyncMatcher.key(sibling))) {
                    sibling.markApplied(itemId, now);
                    repository.saveLine(sibling);
                }
            }
            applied++;
        }
        return applied;
    }
}
