package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.PlanRevenueBreakdown;
import com.vetos.modules.platformadmin.application.dto.PlatformOverviewSummary;
import com.vetos.modules.platformadmin.application.dto.RecentTenantSummary;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Platform admin "Genel Bakis" paneli -- kiraci sayilari, aylik yinelenen
 * gelir (MRR), bu ay fiilen tahsil edilen tutar ve plan dagilimi gibi
 * ust duzey ozet metrikleri tek ekrandan gorebilmek icin. TenantAdminPort
 * uzerinden okur (bkz. docs/architecture.md -- platformadmin'e ozel, yazma
 * da iceren tek istisna port), Plan/PlatformInvoice ise zaten bu modulun
 * kendi domaini (ayri bir port gerekmez).
 */
@Service
@RequiredArgsConstructor
public class GetPlatformOverviewUseCase {

    private static final int RECENT_TENANT_LIMIT = 8;
    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final TenantAdminPort tenantAdminPort;
    private final PlanRepository planRepository;
    private final PlatformInvoiceRepository platformInvoiceRepository;

    public PlatformOverviewSummary execute() {
        List<TenantAdminOverview> tenants = tenantAdminPort.listAll();
        Map<String, Plan> plansByCode = planRepository.findAll().stream()
            .collect(Collectors.toMap(Plan::getCode, p -> p, (a, b) -> a));

        int totalTenants = tenants.size();
        int activeTenants = (int) tenants.stream().filter(t -> t.status() == TenantStatus.ACTIVE).count();
        int suspendedTenants = (int) tenants.stream().filter(t -> t.status() == TenantStatus.SUSPENDED).count();
        int trialBillingTenants = (int) tenants.stream().filter(t -> t.billingStatus() == BillingStatus.TRIAL).count();

        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        int newTenantsLast30Days = (int) tenants.stream().filter(t -> t.createdAt().isAfter(thirtyDaysAgo)).count();

        BigDecimal mrr = tenants.stream()
            .filter(t -> t.billingStatus() == BillingStatus.ACTIVE)
            .map(t -> planPriceOf(plansByCode, t.planCode()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth thisMonth = YearMonth.now(ZONE);
        BigDecimal collectedThisMonth = platformInvoiceRepository.findByStatus(PlatformInvoiceStatus.PAID).stream()
            .filter(inv -> inv.getPaidAt() != null && YearMonth.from(inv.getPaidAt().atZone(ZONE)).equals(thisMonth))
            .map(PlatformInvoice::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PlatformInvoice> overdueInvoices = platformInvoiceRepository.findByStatus(PlatformInvoiceStatus.OVERDUE);
        int overdueInvoiceCount = overdueInvoices.size();
        BigDecimal overdueInvoiceTotal = overdueInvoices.stream()
            .map(PlatformInvoice::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PlanRevenueBreakdown> planBreakdown = buildPlanBreakdown(tenants, plansByCode);

        List<RecentTenantSummary> recentTenants = tenants.stream()
            .sorted(Comparator.comparing(TenantAdminOverview::createdAt).reversed())
            .limit(RECENT_TENANT_LIMIT)
            .map(t -> new RecentTenantSummary(t.tenantId(), t.name(), t.planCode(), t.createdAt()))
            .toList();

        return new PlatformOverviewSummary(
            totalTenants, activeTenants, suspendedTenants, trialBillingTenants, newTenantsLast30Days,
            mrr, collectedThisMonth, overdueInvoiceCount, overdueInvoiceTotal, planBreakdown, recentTenants
        );
    }

    /** CANCELED abonelikler plan dagilimina dahil edilmez -- artik gelir getirmiyorlar. */
    private List<PlanRevenueBreakdown> buildPlanBreakdown(List<TenantAdminOverview> tenants, Map<String, Plan> plansByCode) {
        Map<String, Integer> tenantCountByPlan = new LinkedHashMap<>();
        Map<String, Integer> activeCountByPlan = new LinkedHashMap<>();
        for (TenantAdminOverview t : tenants) {
            if (t.billingStatus() == BillingStatus.CANCELED) continue;
            tenantCountByPlan.merge(t.planCode(), 1, Integer::sum);
            if (t.billingStatus() == BillingStatus.ACTIVE) {
                activeCountByPlan.merge(t.planCode(), 1, Integer::sum);
            }
        }
        return tenantCountByPlan.entrySet().stream()
            .map(e -> {
                String planCode = e.getKey();
                BigDecimal price = planPriceOf(plansByCode, planCode);
                int activeCount = activeCountByPlan.getOrDefault(planCode, 0);
                String planName = plansByCode.containsKey(planCode) ? plansByCode.get(planCode).getName() : planCode;
                return new PlanRevenueBreakdown(planCode, planName, e.getValue(), price.multiply(BigDecimal.valueOf(activeCount)));
            })
            .sorted(Comparator.comparing(PlanRevenueBreakdown::tenantCount).reversed())
            .toList();
    }

    private BigDecimal planPriceOf(Map<String, Plan> plansByCode, String planCode) {
        Plan plan = plansByCode.get(planCode);
        return plan != null ? plan.getMonthlyPrice() : BigDecimal.ZERO;
    }
}
