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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Hekimin sectigi goruntu satirlarini Vetly stoguna isler (yeni kalem ya da miktar esitleme). Islenmis satir tekrar islenmez. */
@Service
@RequiredArgsConstructor
public class ApplyStockSyncUseCase {

    private final TarbilStockSnapshotRepository repository;
    private final TarbilStockSyncPort stockSyncPort;

    @Transactional
    public int execute(UUID tenantId, UUID branchId, UUID snapshotId, List<UUID> lineIds) {
        TarbilStockSnapshot snapshot = repository.findById(snapshotId)
            .filter(s -> s.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilStockSnapshotNotFoundException(snapshotId));
        Set<UUID> wanted = new HashSet<>(lineIds == null ? List.of() : lineIds);
        String system = snapshot.getTarbilSystem().name();
        String category = snapshot.getTarbilSystem() == TarbilStockSystem.HBSAPP_VACCINE ? "Aşı" : "İlaç";
        // Ayni cagrida ayni lot iki kez gelirse ikincisi yeni kalem acmasin: yerel liste her islemde guncellenir.
        List<InventoryStockView> stock = new ArrayList<>(stockSyncPort.listForBranch(branchId));
        Instant now = Instant.now();
        int applied = 0;
        for (TarbilStockSnapshotLine line : repository.findLines(snapshotId)) {
            if (!wanted.contains(line.getId()) || line.isApplied()) {
                continue;
            }
            StockSyncMatcher.Match match = StockSyncMatcher.match(line, stock);
            UUID itemId;
            if (match.status() == TarbilStockSyncStatus.NEW) {
                itemId = stockSyncPort.createFromTarbil(branchId, new NewTarbilStockItem(system, line.getProductName(),
                    line.getPresentation(), category, line.getLotNumber(), line.getExpiryDate(), line.getQuantity()), snapshotId);
            } else {
                itemId = match.item().id();
                stockSyncPort.syncFromTarbil(itemId, line.getQuantity(),
                    new TarbilStockLink(system, line.getProductName(), line.getPresentation()), snapshotId);
                stock.remove(match.item());
            }
            stock.add(new InventoryStockView(itemId, line.getProductName(), line.getLotNumber(), line.getExpiryDate(),
                line.getQuantity(), line.getProductName()));
            line.markApplied(itemId, now);
            repository.saveLine(line);
            applied++;
        }
        return applied;
    }
}
