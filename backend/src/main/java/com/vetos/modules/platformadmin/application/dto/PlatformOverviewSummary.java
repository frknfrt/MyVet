package com.vetos.modules.platformadmin.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlatformOverviewSummary(
    int totalTenants,
    int activeTenants,
    int suspendedTenants,
    int trialBillingTenants,
    int newTenantsLast30Days,
    BigDecimal monthlyRecurringRevenue,
    BigDecimal collectedThisMonth,
    int overdueInvoiceCount,
    BigDecimal overdueInvoiceTotal,
    List<PlanRevenueBreakdown> planBreakdown,
    List<RecentTenantSummary> recentTenants
) {}
