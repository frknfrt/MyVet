package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.*;
import com.vetos.modules.tenant.domain.BillableSubscription;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateDueInvoicesUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlanRepository planRepository;
    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private PlatformBillingEmailPort platformBillingEmailPort;
    @Mock private PlatformBillingSmsPort platformBillingSmsPort;

    @Test
    void should_generateInvoiceAndAdvanceRenewalAndNotify_when_subscriptionIsDue() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        BillableSubscription subscription = new BillableSubscription(tenantId, "PRO", today);
        Plan plan = Plan.create("PRO", "Pro Plan", new BigDecimal("500.00"));

        when(tenantAdminPort.listSubscriptionsDueOnOrBefore(today)).thenReturn(List.of(subscription));
        when(platformInvoiceRepository.findByTenantIdAndPeriodStart(tenantId, today)).thenReturn(Optional.empty());
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.of("admin@klinik.com"));
        when(tenantAdminPort.findBillingContactPhone(tenantId)).thenReturn(Optional.of("+905551112233"));

        GenerateDueInvoicesUseCase useCase = new GenerateDueInvoicesUseCase(
            tenantAdminPort, planRepository, platformInvoiceRepository, platformBillingEmailPort, platformBillingSmsPort
        );
        useCase.execute(today);

        verify(platformInvoiceRepository).save(any(PlatformInvoice.class));
        verify(tenantAdminPort).advanceRenewal(tenantId, today.plusMonths(1));
        verify(platformBillingEmailPort).sendInvoiceIssued(any(), eq("Test Klinik"), eq("admin@klinik.com"));
        verify(platformBillingSmsPort).sendInvoiceIssued(any(), eq("Test Klinik"), eq("+905551112233"));
    }

    @Test
    void should_skipTenant_when_invoiceAlreadyExistsForPeriod() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        BillableSubscription subscription = new BillableSubscription(tenantId, "PRO", today);
        PlatformInvoice existing = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);

        when(tenantAdminPort.listSubscriptionsDueOnOrBefore(today)).thenReturn(List.of(subscription));
        when(platformInvoiceRepository.findByTenantIdAndPeriodStart(tenantId, today)).thenReturn(Optional.of(existing));

        GenerateDueInvoicesUseCase useCase = new GenerateDueInvoicesUseCase(
            tenantAdminPort, planRepository, platformInvoiceRepository, platformBillingEmailPort, platformBillingSmsPort
        );
        useCase.execute(today);

        verify(platformInvoiceRepository, never()).save(any());
        verify(tenantAdminPort, never()).advanceRenewal(any(), any());
    }

    @Test
    void should_skipTenant_when_planNoLongerExists() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        BillableSubscription subscription = new BillableSubscription(tenantId, "GHOST", today);

        when(tenantAdminPort.listSubscriptionsDueOnOrBefore(today)).thenReturn(List.of(subscription));
        when(platformInvoiceRepository.findByTenantIdAndPeriodStart(tenantId, today)).thenReturn(Optional.empty());
        when(planRepository.findByCode("GHOST")).thenReturn(Optional.empty());

        GenerateDueInvoicesUseCase useCase = new GenerateDueInvoicesUseCase(
            tenantAdminPort, planRepository, platformInvoiceRepository, platformBillingEmailPort, platformBillingSmsPort
        );
        useCase.execute(today);

        verify(platformInvoiceRepository, never()).save(any());
    }

    private TenantAdminOverview overview(UUID tenantId) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.ACTIVE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 28), 1, 3
        );
    }
}
