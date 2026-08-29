package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantBillingReconcilerTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private TenantAdminPort tenantAdminPort;

    private TenantBillingReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new TenantBillingReconciler(platformInvoiceRepository, tenantAdminPort);
    }

    @Test
    void should_notRelaxBillingStatus_when_anotherInvoiceIsStillIssued() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice resolvedInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(resolvedInvoice, "id", UUID.randomUUID());
        PlatformInvoice stillIssuedInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(stillIssuedInvoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(resolvedInvoice, stillIssuedInvoice));

        reconciler.reconcileAfterInvoiceResolved(tenantId, resolvedInvoice.getId());

        verify(tenantAdminPort, never()).updateBillingStatus(eq(tenantId), any(BillingStatus.class));
        verify(tenantAdminPort, never()).activate(tenantId);
    }

    @Test
    void should_notRelaxBillingStatus_when_anotherInvoiceIsStillOverdue() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice resolvedInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(resolvedInvoice, "id", UUID.randomUUID());
        PlatformInvoice overdueInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today.minusMonths(2), today.minusMonths(1), today.minusMonths(1));
        overdueInvoice.markOverdue();
        ReflectionTestUtils.setField(overdueInvoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(resolvedInvoice, overdueInvoice));

        reconciler.reconcileAfterInvoiceResolved(tenantId, resolvedInvoice.getId());

        verify(tenantAdminPort, never()).updateBillingStatus(eq(tenantId), any(BillingStatus.class));
        verify(tenantAdminPort, never()).activate(tenantId);
    }

    @Test
    void should_relaxBillingStatusAndReactivate_when_noOtherInvoiceIsOutstanding() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice resolvedInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(resolvedInvoice, "id", UUID.randomUUID());
        PlatformInvoice unrelatedPaidInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today.minusMonths(1), today, today.minusMonths(1));
        unrelatedPaidInvoice.markPaid();
        ReflectionTestUtils.setField(unrelatedPaidInvoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(resolvedInvoice, unrelatedPaidInvoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId, TenantStatus.SUSPENDED));

        reconciler.reconcileAfterInvoiceResolved(tenantId, resolvedInvoice.getId());

        verify(tenantAdminPort).updateBillingStatus(tenantId, BillingStatus.ACTIVE);
        verify(tenantAdminPort).activate(tenantId);
    }

    @Test
    void should_relaxBillingStatusWithoutActivating_when_noOtherInvoiceIsOutstandingAndTenantAlreadyActive() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice resolvedInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(resolvedInvoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(resolvedInvoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId, TenantStatus.ACTIVE));

        reconciler.reconcileAfterInvoiceResolved(tenantId, resolvedInvoice.getId());

        verify(tenantAdminPort).updateBillingStatus(tenantId, BillingStatus.ACTIVE);
        verify(tenantAdminPort, never()).activate(tenantId);
    }

    private TenantAdminOverview overview(UUID tenantId, TenantStatus status) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", status, Instant.now(), "PRO", BillingStatus.PAST_DUE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 28), 1, 3
        );
    }
}
