package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordPlatformPaymentUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private PlatformPaymentRepository platformPaymentRepository;
    @Mock private TenantAdminPort tenantAdminPort;

    private RecordPlatformPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantBillingReconciler tenantBillingReconciler = new TenantBillingReconciler(platformInvoiceRepository, tenantAdminPort);
        useCase = new RecordPlatformPaymentUseCase(platformInvoiceRepository, platformPaymentRepository, tenantBillingReconciler);
    }

    @Test
    void should_markInvoicePaidAndReactivateTenant_when_tenantWasSuspended() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        UUID adminId = UUID.randomUUID();
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(platformInvoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId, TenantStatus.SUSPENDED));

        useCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), new BigDecimal("500.00"), PlatformPaymentMethod.BANK_TRANSFER, today, "Havale ref: 1", adminId
        ));

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.PAID);
        verify(platformPaymentRepository).save(any(PlatformPayment.class));
        verify(tenantAdminPort).updateBillingStatus(tenantId, BillingStatus.ACTIVE);
        verify(tenantAdminPort).activate(tenantId);
    }

    @Test
    void should_notReactivateTenant_when_tenantWasAlreadyActive() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(platformInvoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId, TenantStatus.ACTIVE));

        useCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), new BigDecimal("500.00"), PlatformPaymentMethod.CARD, today, null, UUID.randomUUID()
        ));

        verify(tenantAdminPort, never()).activate(tenantId);
    }

    @Test
    void should_notReactivateTenant_when_anotherInvoiceIsStillOverdue() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice overdueInvoiceA = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today.minusMonths(2), today.minusMonths(1), today.minusMonths(1));
        overdueInvoiceA.markOverdue();
        ReflectionTestUtils.setField(overdueInvoiceA, "id", UUID.randomUUID());
        PlatformInvoice invoiceB = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoiceB, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoiceB.getId())).thenReturn(Optional.of(invoiceB));
        when(platformInvoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(overdueInvoiceA, invoiceB));

        useCase.execute(new RecordPlatformPaymentCommand(
            invoiceB.getId(), new BigDecimal("500.00"), PlatformPaymentMethod.CARD, today, null, UUID.randomUUID()
        ));

        assertThat(invoiceB.getStatus()).isEqualTo(PlatformInvoiceStatus.PAID);
        verify(tenantAdminPort, never()).updateBillingStatus(eq(tenantId), any(BillingStatus.class));
        verify(tenantAdminPort, never()).activate(tenantId);
    }

    @Test
    void should_throwPlatformInvoiceNotFoundException_when_invoiceDoesNotExist() {
        UUID invoiceId = UUID.randomUUID();
        when(platformInvoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RecordPlatformPaymentCommand(
            invoiceId, BigDecimal.TEN, PlatformPaymentMethod.OTHER, LocalDate.of(2026, 8, 28), null, UUID.randomUUID()
        ))).isInstanceOf(PlatformInvoiceNotFoundException.class);
    }

    @Test
    void should_throwInvalidTransition_when_invoiceAlreadyPaid() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 28);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        invoice.markPaid();
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), new BigDecimal("500.00"), PlatformPaymentMethod.CARD, today, null, UUID.randomUUID()
        ))).isInstanceOf(PlatformInvoiceInvalidTransitionException.class);
    }

    private TenantAdminOverview overview(UUID tenantId, TenantStatus status) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", status, Instant.now(), "PRO", BillingStatus.PAST_DUE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 28), 1, 3
        );
    }
}
