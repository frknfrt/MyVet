package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchComparisonLine(
    UUID branchId, String branchName, long invoiceCount, BigDecimal totalRevenue, BigDecimal paidRevenue
) {}
