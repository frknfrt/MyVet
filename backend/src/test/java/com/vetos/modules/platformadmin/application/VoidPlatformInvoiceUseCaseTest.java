package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceInvalidTransitionException;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoidPlatformInvoiceUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private TenantAdminPort tenantAdminPort;

    private VoidPlatformInvoiceUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantBillingReconciler tenantBillingReconciler = new TenantBillingReconciler(platformInvoiceRepository, tenantAdminPort);
        useCase = new VoidPlatformInvoiceUseCase(platformInvoiceRepository, tenantBillingReconciler);
    }

    @Test
    void should_voidInvoice_when_statusIsIssued() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        useCase.execute(invoice.getId());

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.VOID);
    }

    @Test
    void should_notTouchTenantBillingStatus_when_voidedInvoiceWasNeverOverdue() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        useCase.execute(invoice.getId());

        verifyNoInteractions(tenantAdminPort);
        verify(platformInvoiceRepository, never()).findByTenantId(any());
    }

    @Test
    void should_reactivateSuspendedTenant_when_voidingTheirOnlyOverdueInvoice() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today.minusMonths(1), today.minusDays(20), today.minusDays(20));
        invoice.markOverdue();
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(platformInvoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId, TenantStatus.SUSPENDED));

        useCase.execute(invoice.getId());

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.VOID);
        verify(tenantAdminPort).updateBillingStatus(tenantId, BillingStatus.ACTIVE);
        verify(tenantAdminPort).activate(tenantId);
    }

    @Test
    void should_notReactivateTenant_when_voidingOverdueInvoiceButAnotherInvoiceIsStillOutstanding() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice overdueInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today.minusMonths(1), today.minusDays(20), today.minusDays(20));
        overdueInvoice.markOverdue();
        ReflectionTestUtils.setField(overdueInvoice, "id", UUID.randomUUID());
        PlatformInvoice otherIssuedInvoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(otherIssuedInvoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(overdueInvoice.getId())).thenReturn(Optional.of(overdueInvoice));
        when(platformInvoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(overdueInvoice, otherIssuedInvoice));

        useCase.execute(overdueInvoice.getId());

        assertThat(overdueInvoice.getStatus()).isEqualTo(PlatformInvoiceStatus.VOID);
        verify(tenantAdminPort, never()).updateBillingStatus(eq(tenantId), any(BillingStatus.class));
        verify(tenantAdminPort, never()).activate(tenantId);
    }

    @Test
    void should_throwPlatformInvoiceNotFoundException_when_invoiceDoesNotExist() {
        UUID invoiceId = UUID.randomUUID();
        when(platformInvoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(invoiceId))
            .isInstanceOf(PlatformInvoiceNotFoundException.class);
    }

    @Test
    void should_throwInvalidTransition_when_invoiceAlreadyPaid() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        invoice.markPaid();
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(invoice.getId()))
            .isInstanceOf(PlatformInvoiceInvalidTransitionException.class);
    }

    private TenantAdminOverview overview(UUID tenantId, TenantStatus status) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", status, Instant.now(), "PRO", BillingStatus.PAST_DUE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 28), 1, 3
        );
    }
}
