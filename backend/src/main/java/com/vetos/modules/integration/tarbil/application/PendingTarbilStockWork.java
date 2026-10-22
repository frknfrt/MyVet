package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * TARBIL'e henuz islenmemis (PENDING) aktarimlarin dokundugu stok kalemleri (2026-10-21). Boyle bir kalemde Vetly ile
 * TARBIL miktari gecici olarak farklidir; esitlemek yanlis olur (aktarim sonradan TARBIL'den duser, Vetly tekrar dusmez).
 * Kaynaklar: TARBIL onayini bekleyen stok dusumu satirlari ve asi kaydinin stok kalemi (eski akista Vetly hemen duserdi).
 */
@Component
@RequiredArgsConstructor
class PendingTarbilStockWork {

    private final TarbilSubmissionRepository submissions;
    private final TarbilStockDeductionPort stockDeductionPort;
    private final TarbilSubmissionAssembler assembler;

    /** Stok kalemi -> o kalemi etkileyen bekleyen aktarim sayisi. */
    Map<UUID, Integer> countByItem(UUID tenantId) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (TarbilSubmission s : submissions.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)) {
            Set<UUID> items = new HashSet<>();
            StockReferenceType ref = s.sourceKindOrDefault().stockReference();
            if (ref != null) {
                stockDeductionPort.listForReference(ref, s.getSourceId()).stream()
                    .filter(l -> l.status() == PendingDeductionStatus.PENDING)
                    .forEach(l -> items.add(l.inventoryItemId()));
            }
            assembler.liveVaccination(s)
                .filter(v -> v.inventoryItemId() != null)
                .ifPresent(v -> items.add(v.inventoryItemId()));
            items.forEach(id -> counts.merge(id, 1, Integer::sum));
        }
        return counts;
    }
}
