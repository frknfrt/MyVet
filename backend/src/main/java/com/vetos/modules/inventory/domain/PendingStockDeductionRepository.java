package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.UUID;

public interface PendingStockDeductionRepository {
    PendingStockDeduction save(PendingStockDeduction deduction);

    List<PendingStockDeduction> findByReference(StockReferenceType type, UUID referenceId);

    /** Kiraci filtresi @TenantId ile gelir. */
    List<PendingStockDeduction> findAllPending();
}
