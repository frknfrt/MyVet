package com.vetos.modules.inventory.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PendingStockDeductionRepository {
    PendingStockDeduction save(PendingStockDeduction deduction);

    List<PendingStockDeduction> findByReference(StockReferenceType type, UUID referenceId);

    /**
     * Satiri yalniz hala PENDING ise atomik olarak DEDUCTED yapar (kosullu UPDATE). Es zamanli iki onaydan yalniz biri
     * true alir; stok hareketi yalniz o yazar (review 2026-10-10, cift dusum).
     */
    boolean claimForSettlement(UUID id, int settledQuantity, DeductionSettlement via, UUID staffId, Instant now);

    /** Kiraci filtresi @TenantId ile gelir. */
    List<PendingStockDeduction> findAllPending();
}
