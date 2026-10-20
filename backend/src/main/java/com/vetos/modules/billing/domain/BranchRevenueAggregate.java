package com.vetos.modules.billing.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Sube bazli ciro toplami; toplama veritabaninda (bkz. InvoiceRepository#sumIssuedByBranchBetween). */
public record BranchRevenueAggregate(UUID branchId, BigDecimal totalAmount) {

    public BranchRevenueAggregate {
        totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    }
}
