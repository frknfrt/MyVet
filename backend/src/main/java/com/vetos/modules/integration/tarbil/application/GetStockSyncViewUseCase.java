package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.StockSyncLineView;
import com.vetos.modules.integration.tarbil.application.dto.StockSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.PresentationUnitGuesser;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetStockSyncViewUseCase {

    private final TarbilStockSnapshotRepository repository;
    private final TarbilStockSyncPort stockSyncPort;
    private final PendingTarbilStockWork pendingWork;

    @Transactional(readOnly = true)
    public StockSyncView execute(UUID tenantId, UUID branchId, TarbilStockSystem system) {
        Optional<TarbilStockSnapshot> snapshot = repository.findLatest(tenantId, system);
        if (snapshot.isEmpty()) {
            return new StockSyncView(null, system, null, List.of());
        }
        List<InventoryStockView> stock = stockSyncPort.listForBranch(branchId);
        List<TarbilStockSnapshotLine> rows = repository.findLines(snapshot.get().getId());
        Map<String, StockSyncMatcher.Target> targets = StockSyncMatcher.targets(system, snapshot.get().isPartStockRead(), rows);
        Map<UUID, Integer> pending = pendingWork.countByItem(tenantId);
        // Spec 2026-10-24: urun+seri basina tek satir (butun stok + parca stok ayni satirda); lineId anahtarin ilk satiri.
        Map<String, TarbilStockSnapshotLine> first = new LinkedHashMap<>();
        Set<String> hasPart = new HashSet<>();
        for (TarbilStockSnapshotLine l : rows) {
            first.putIfAbsent(StockSyncMatcher.key(l), l);
            if (l.isPartStock()) {
                hasPart.add(StockSyncMatcher.key(l));
            }
        }
        List<StockSyncLineView> lines = first.entrySet().stream().map(e -> {
            TarbilStockSnapshotLine l = e.getValue();
            StockSyncMatcher.Target t = targets.get(e.getKey());
            StockSyncMatcher.Match m = StockSyncMatcher.match(l, stock, t);
            String unit = m.item() != null ? m.item().unit()
                : PresentationUnitGuesser.guess(system.name(), l.getPresentation(), t.dosesPerPackage()).unit().name();
            return new StockSyncLineView(l.getId(), l.getProductName(), l.getPresentation(), l.getLotNumber(), l.getExpiryDate(),
                t.closed(), t.opened(), m.status(),
                m.item() == null ? null : m.item().id(), m.item() == null ? null : m.item().quantityOnHand(),
                m.item() == null ? null : m.item().openedQuantity(), unit,
                hasPart.contains(e.getKey()), pendingCount(l, m, pending));
        }).toList();
        return new StockSyncView(snapshot.get().getId(), system, snapshot.get().getTakenAt(), lines);
    }

    /** Islenmis satirda gosterilmez; uyari yalniz hekimin karar verecegi satirlar icin. */
    private static int pendingCount(TarbilStockSnapshotLine line, StockSyncMatcher.Match m, Map<UUID, Integer> pending) {
        if (line.isApplied() || m.item() == null) {
            return 0;
        }
        return pending.getOrDefault(m.item().id(), 0);
    }
}
