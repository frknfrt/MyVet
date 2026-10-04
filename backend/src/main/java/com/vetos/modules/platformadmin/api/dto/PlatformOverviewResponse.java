package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.application.dto.PlatformOverviewSummary;

import java.math.BigDecimal;
import java.util.List;

public record PlatformOverviewResponse(
    int totalTenants,
    int activeTenants,
    int suspendedTenants,
    int trialBillingTenants,
    int newTenantsLast30Days,
    BigDecimal monthlyRecurringRevenue,
    BigDecimal collectedThisMonth,
    int overdueInvoiceCount,
    BigDecimal overdueInvoiceTotal,
    List<PlanRevenueBreakdownResponse> planBreakdown,
    List<RecentTenantResponse> recentTenants,
    List<ChurnReasonBreakdownResponse> churnBreakdown
) {
    public static PlatformOverviewResponse from(PlatformOverviewSummary s) {
        return new PlatformOverviewResponse(
            s.totalTenants(), s.activeTenants(), s.suspendedTenants(), s.trialBillingTenants(), s.newTenantsLast30Days(),
            s.monthlyRecurringRevenue(), s.collectedThisMonth(), s.overdueInvoiceCount(), s.overdueInvoiceTotal(),
            s.planBreakdown().stream().map(PlanRevenueBreakdownResponse::from).toList(),
            s.recentTenants().stream().map(RecentTenantResponse::from).toList(),
            s.churnBreakdown().stream().map(ChurnReasonBreakdownResponse::from).toList()
        );
    }
}
