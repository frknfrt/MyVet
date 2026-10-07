package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.StockComparison;
import com.vetos.modules.integration.tarbil.application.dto.StockSnapshotLineInput;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilStockSnapshotException;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilStockBranchMissingException;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Eklenti TARBIL stok sayfasi acilinca (2026-10-07): okunan satirlar eklentiyi eslestiren personelin subesinin stoguyla
 * karsilastirilir; Vetly'de olmayan / miktari farkli urun varsa kart gosterilir. HICBIR SEY kaydedilmez -- gonderim ve
 * isleme bugunku akisla (RecordStockSnapshot, ApplyStockSync) hekim onayiyla yapilir. Esleme kurali StockSyncMatcher'dakiyle ayni.
 */
@Service
@RequiredArgsConstructor
public class CompareStockSnapshotUseCase {

    private final StaffUserLookupPort staffUserLookupPort;
    private final TarbilStockSyncPort stockSyncPort;

    @Transactional(readOnly = true)
    public StockComparison execute(UUID tenantId, UUID staffUserId, TarbilStockSystem system, List<StockSnapshotLineInput> inputs) {
        if (inputs != null && inputs.size() > RecordStockSnapshotUseCase.MAX_LINES) {
            throw new InvalidTarbilStockSnapshotException("satir sayisi en cok " + RecordStockSnapshotUseCase.MAX_LINES + " olmali");
        }
        UUID branchId = staffUserLookupPort.findSummaryById(staffUserId).branchId();
        if (branchId == null) {
            throw new TarbilStockBranchMissingException();
        }
        List<InventoryStockView> stock = stockSyncPort.listForBranch(branchId);
        List<TarbilStockSnapshotLine> lines = new ArrayList<>();
        for (int i = 0; inputs != null && i < inputs.size(); i++) {
            StockSnapshotLineInput l = inputs.get(i);
            if (l.productName() == null || l.productName().isBlank()) continue;
            lines.add(TarbilStockSnapshotLine.of(null, tenantId, i + 1, l.productName().trim(), l.presentation(),
                l.lotNumber() == null || l.lotNumber().isBlank() ? null : l.lotNumber().trim(), l.expiryDate(), l.quantity(), l.openedQuantity()));
        }
        Map<String, Integer> totals = StockSyncMatcher.totals(lines);
        Set<String> seen = new HashSet<>();
        int fresh = 0;
        int differs = 0;
        int matched = 0;
        for (TarbilStockSnapshotLine line : lines) {
            String key = StockSyncMatcher.key(line);
            if (!seen.add(key)) continue;
            switch (StockSyncMatcher.match(line, stock, totals.get(key)).status()) {
                case NEW -> fresh++;
                case QUANTITY_DIFFERS -> differs++;
                default -> matched++;
            }
        }
        return new StockComparison(fresh, differs, matched);
    }
}
