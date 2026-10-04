package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.StockSyncLineView;
import com.vetos.modules.integration.tarbil.application.dto.StockSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshot;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetStockSyncViewUseCase {

    private final TarbilStockSnapshotRepository repository;
    private final TarbilStockSyncPort stockSyncPort;

    @Transactional(readOnly = true)
    public StockSyncView execute(UUID tenantId, UUID branchId, TarbilStockSystem system) {
        Optional<TarbilStockSnapshot> snapshot = repository.findLatest(tenantId, system);
        if (snapshot.isEmpty()) {
            return new StockSyncView(null, system, null, List.of());
        }
        List<InventoryStockView> stock = stockSyncPort.listForBranch(branchId);
        List<TarbilStockSnapshotLine> rows = repository.findLines(snapshot.get().getId());
        Map<String, Integer> totals = StockSyncMatcher.totals(rows);
        List<StockSyncLineView> lines = rows.stream().map(l -> {
            StockSyncMatcher.Match m = StockSyncMatcher.match(l, stock, totals.get(StockSyncMatcher.key(l)));
            return new StockSyncLineView(l.getId(), l.getProductName(), l.getPresentation(), l.getLotNumber(), l.getExpiryDate(),
                l.getQuantity(), l.getOpenedQuantity(), m.status(),
                m.item() == null ? null : m.item().id(), m.item() == null ? null : m.item().quantityOnHand());
        }).toList();
        return new StockSyncView(snapshot.get().getId(), system, snapshot.get().getTakenAt(), lines);
    }
}
