package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record RevenueSummary(
    List<MonthlyRevenue> monthlyTrend,
    List<BranchRevenue> branchBreakdown,
    BigDecimal currentMonthRevenue,
    BigDecimal previousMonthRevenue
) {}
