package com.vetos.modules.platformadmin.application;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitiateInvoiceCheckoutUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PaymentGatewayPort paymentGatewayPort;

    private InitiateInvoiceCheckoutUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InitiateInvoiceCheckoutUseCase(platformInvoiceRepository, tenantAdminPort, paymentGatewayPort);
    }

    @Test
    void should_initializeCheckout_when_invoiceIsIssuedAndOwnedByTenant() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.of("klinik@example.com"));
        CheckoutSession expectedSession = new CheckoutSession("https://sandbox.iyzipay.com/pay/abc", "abc");
        when(paymentGatewayPort.initializeCheckout(invoice.getId().toString(), invoice.getAmount(), "Test Klinik", "klinik@example.com"))
            .thenReturn(expectedSession);

        CheckoutSession result = useCase.execute(tenantId, invoice.getId());

        assertThat(result).isEqualTo(expectedSession);
    }

    @Test
    void should_fallBackToDefaultEmail_when_noBillingContactEmail() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.empty());
        when(paymentGatewayPort.initializeCheckout(eq(invoice.getId().toString()), any(), any(), eq("destek@myvet.app")))
            .thenReturn(new CheckoutSession("https://sandbox.iyzipay.com/pay/abc", "abc"));

        useCase.execute(tenantId, invoice.getId());
    }

    @Test
    void should_throwNotFound_when_invoiceBelongsToAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(otherTenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(tenantId, invoice.getId()))
            .isInstanceOf(PlatformInvoiceNotFoundException.class);
    }

    @Test
    void should_throwNotFound_when_invoiceDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(platformInvoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(tenantId, invoiceId))
            .isInstanceOf(PlatformInvoiceNotFoundException.class);
    }

    @Test
    void should_throwInvalidTransition_when_invoiceAlreadyPaid() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 8, 30);
        PlatformInvoice invoice = PlatformInvoice.issue(tenantId, "PRO", new BigDecimal("500.00"), today, today.plusMonths(1), today);
        invoice.markPaid();
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(platformInvoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(tenantId, invoice.getId()))
            .isInstanceOf(PlatformInvoiceInvalidTransitionException.class);
    }

    private TenantAdminOverview overview(UUID tenantId) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.PAST_DUE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 30), 1, 3
        );
    }
}
