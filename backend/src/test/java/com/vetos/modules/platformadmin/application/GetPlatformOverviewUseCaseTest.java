package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.PlanRevenueBreakdown;
import com.vetos.modules.platformadmin.application.dto.ChurnReasonBreakdown;
import com.vetos.modules.platformadmin.application.dto.PlatformOverviewSummary;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import com.vetos.modules.tenant.domain.TenantSuspensionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPlatformOverviewUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlanRepository planRepository;
    @Mock private PlatformInvoiceRepository platformInvoiceRepository;

    private TenantAdminOverview tenant(
        String name, TenantStatus status, BillingStatus billingStatus, String planCode, Instant createdAt
    ) {
        return new TenantAdminOverview(
            UUID.randomUUID(), name, "1111111111", status, createdAt, planCode, billingStatus,
            LocalDate.now(), LocalDate.now().plusMonths(1), 1, 1, null, null
        );
    }

    private TenantAdminOverview suspendedTenant(String name, String planCode, Instant createdAt, TenantSuspensionReason reason) {
        return new TenantAdminOverview(
            UUID.randomUUID(), name, "1111111111", TenantStatus.SUSPENDED, createdAt, planCode, BillingStatus.PAST_DUE,
            LocalDate.now(), LocalDate.now().plusMonths(1), 1, 1, reason, null
        );
    }

    @Test
    void should_aggregateTenantCountsRevenueAndPlanBreakdown() {
        Instant now = Instant.now();
        Instant recentlyJoined = now.minus(5, ChronoUnit.DAYS);
        Instant longAgo = now.minus(400, ChronoUnit.DAYS);

        List<TenantAdminOverview> tenants = List.of(
            tenant("Ulukavak", TenantStatus.ACTIVE, BillingStatus.ACTIVE, "PRO", recentlyJoined),
            tenant("Mutlu Klinik", TenantStatus.ACTIVE, BillingStatus.ACTIVE, "PRO", longAgo),
            tenant("Deneme Klinik", TenantStatus.ACTIVE, BillingStatus.TRIAL, "PRO", recentlyJoined),
            tenant("Askidaki Klinik", TenantStatus.SUSPENDED, BillingStatus.PAST_DUE, "BASIC", longAgo),
            tenant("Eski Klinik", TenantStatus.ACTIVE, BillingStatus.CANCELED, "BASIC", longAgo)
        );
        when(tenantAdminPort.listAll()).thenReturn(tenants);

        Plan proPlan = Plan.create("PRO", "Pro Plan", new BigDecimal("500.00"));
        Plan basicPlan = Plan.create("BASIC", "Temel Plan", new BigDecimal("250.00"));
        when(planRepository.findAll()).thenReturn(List.of(proPlan, basicPlan));

        PlatformInvoice paidThisMonth = PlatformInvoice.issue(
            UUID.randomUUID(), "PRO", new BigDecimal("500.00"), LocalDate.now().minusDays(10), LocalDate.now(), LocalDate.now().minusDays(10)
        );
        paidThisMonth.markPaid();
        when(platformInvoiceRepository.findByStatus(PlatformInvoiceStatus.PAID)).thenReturn(List.of(paidThisMonth));

        PlatformInvoice overdue = PlatformInvoice.issue(
            UUID.randomUUID(), "BASIC", new BigDecimal("250.00"),
            LocalDate.now().minusMonths(1), LocalDate.now(), LocalDate.now().minusMonths(1)
        );
        overdue.markOverdue();
        when(platformInvoiceRepository.findByStatus(PlatformInvoiceStatus.OVERDUE)).thenReturn(List.of(overdue));

        GetPlatformOverviewUseCase useCase = new GetPlatformOverviewUseCase(tenantAdminPort, planRepository, platformInvoiceRepository);
        PlatformOverviewSummary summary = useCase.execute();

        assertThat(summary.totalTenants()).isEqualTo(5);
        assertThat(summary.activeTenants()).isEqualTo(4);
        assertThat(summary.suspendedTenants()).isEqualTo(1);
        assertThat(summary.trialBillingTenants()).isEqualTo(1);
        assertThat(summary.newTenantsLast30Days()).isEqualTo(2);
        // MRR: sadece billingStatus=ACTIVE olanlar -- Ulukavak + Mutlu Klinik, ikisi de PRO (500)
        assertThat(summary.monthlyRecurringRevenue()).isEqualByComparingTo("1000.00");
        assertThat(summary.collectedThisMonth()).isEqualByComparingTo("500.00");
        assertThat(summary.overdueInvoiceCount()).isEqualTo(1);
        assertThat(summary.overdueInvoiceTotal()).isEqualByComparingTo("250.00");

        // CANCELED olan "Eski Klinik" plan dagilimina dahil edilmemeli
        assertThat(summary.planBreakdown()).extracting(PlanRevenueBreakdown::planCode).containsExactlyInAnyOrder("PRO", "BASIC");
        PlanRevenueBreakdown proBreakdown = summary.planBreakdown().stream().filter(b -> b.planCode().equals("PRO")).findFirst().orElseThrow();
        assertThat(proBreakdown.tenantCount()).isEqualTo(3); // 2 ACTIVE + 1 TRIAL
        assertThat(proBreakdown.monthlyRevenue()).isEqualByComparingTo("1000.00"); // sadece ACTIVE olanlar gelir sayilir
        PlanRevenueBreakdown basicBreakdown = summary.planBreakdown().stream().filter(b -> b.planCode().equals("BASIC")).findFirst().orElseThrow();
        assertThat(basicBreakdown.tenantCount()).isEqualTo(1); // sadece "Askidaki Klinik" (PAST_DUE) -- CANCELED olan sayilmaz
        assertThat(basicBreakdown.monthlyRevenue()).isEqualByComparingTo("0.00"); // PAST_DUE, ACTIVE degil

        assertThat(summary.recentTenants()).hasSize(5);
        assertThat(summary.recentTenants().get(0).name()).isIn("Ulukavak", "Deneme Klinik"); // en yeni ikisi

        // "Askidaki Klinik" suspensionReason=null ile askiya alinmis (bu ozellik oncesi senaryosu) -- UNKNOWN kovasina girer
        assertThat(summary.churnBreakdown()).hasSize(1);
        assertThat(summary.churnBreakdown().get(0).reason()).isEqualTo("UNKNOWN");
        assertThat(summary.churnBreakdown().get(0).count()).isEqualTo(1);
    }

    @Test
    void should_groupChurnBreakdown_byActualSuspensionReason() {
        List<TenantAdminOverview> tenants = List.of(
            suspendedTenant("Fiyat Yuzunden Ayrilan", "PRO", Instant.now(), TenantSuspensionReason.PRICE),
            suspendedTenant("Rakibe Gecen", "PRO", Instant.now(), TenantSuspensionReason.COMPETITOR),
            suspendedTenant("Fiyat Yuzunden Ayrilan 2", "BASIC", Instant.now(), TenantSuspensionReason.PRICE)
        );
        when(tenantAdminPort.listAll()).thenReturn(tenants);
        when(planRepository.findAll()).thenReturn(List.of());
        when(platformInvoiceRepository.findByStatus(PlatformInvoiceStatus.PAID)).thenReturn(List.of());
        when(platformInvoiceRepository.findByStatus(PlatformInvoiceStatus.OVERDUE)).thenReturn(List.of());

        GetPlatformOverviewUseCase useCase = new GetPlatformOverviewUseCase(tenantAdminPort, planRepository, platformInvoiceRepository);
        PlatformOverviewSummary summary = useCase.execute();

        assertThat(summary.churnBreakdown()).extracting(ChurnReasonBreakdown::reason, ChurnReasonBreakdown::count)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("PRICE", 2),
                org.assertj.core.groups.Tuple.tuple("COMPETITOR", 1)
            );
    }

    @Test
    void should_returnZeroes_when_noTenants() {
        when(tenantAdminPort.listAll()).thenReturn(List.of());
        when(planRepository.findAll()).thenReturn(List.of());
        when(platformInvoiceRepository.findByStatus(PlatformInvoiceStatus.PAID)).thenReturn(List.of());
        when(platformInvoiceRepository.findByStatus(PlatformInvoiceStatus.OVERDUE)).thenReturn(List.of());

        GetPlatformOverviewUseCase useCase = new GetPlatformOverviewUseCase(tenantAdminPort, planRepository, platformInvoiceRepository);
        PlatformOverviewSummary summary = useCase.execute();

        assertThat(summary.totalTenants()).isZero();
        assertThat(summary.monthlyRecurringRevenue()).isEqualByComparingTo("0");
        assertThat(summary.planBreakdown()).isEmpty();
        assertThat(summary.recentTenants()).isEmpty();
        assertThat(summary.churnBreakdown()).isEmpty();
    }
}
