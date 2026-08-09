package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.BranchComparisonLine;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchComparisonLineResponse(
    UUID branchId, String branchName, long invoiceCount, BigDecimal totalRevenue, BigDecimal paidRevenue
) {
    public static BranchComparisonLineResponse from(BranchComparisonLine l) {
        return new BranchComparisonLineResponse(
            l.branchId(), l.branchName(), l.invoiceCount(), l.totalRevenue(), l.paidRevenue()
        );
    }
}
