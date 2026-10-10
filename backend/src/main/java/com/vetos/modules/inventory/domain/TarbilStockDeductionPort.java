package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.UUID;

/**
 * integration/tarbil icin (spec 2026-10-09 S6): bekleyen TARBIL dusumlerini okur ve TARBIL onayi gelince duser.
 * Kiraci TenantContext'ten gelir. reported == null -> planlanan adetler (MANUAL onay ya da satir bildirmeyen eklenti).
 */
public interface TarbilStockDeductionPort {
    List<PendingDeductionLine> listForReference(StockReferenceType type, UUID referenceId);

    DeductionSettlementResult settleFromTarbil(StockReferenceType type, UUID referenceId, List<ReportedDeductionLine> reported, UUID staffId);
}
