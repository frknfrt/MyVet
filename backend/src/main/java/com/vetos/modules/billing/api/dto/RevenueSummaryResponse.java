package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.BranchRevenue;
import com.vetos.modules.billing.application.dto.MonthlyRevenue;
import com.vetos.modules.billing.application.dto.RevenueSummary;

import java.math.BigDecimal;
import java.util.List;

public record RevenueSummaryResponse(
    List<MonthlyRevenue> monthlyTrend,
    List<BranchRevenue> branchBreakdown,
    BigDecimal currentMonthRevenue,
    BigDecimal previousMonthRevenue
) {
    public static RevenueSummaryResponse from(RevenueSummary s) {
        return new RevenueSummaryResponse(s.monthlyTrend(), s.branchBreakdown(), s.currentMonthRevenue(), s.previousMonthRevenue());
    }
}
