package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.*;
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
class RemindDueSoonInvoicesUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlatformBillingEmailPort platformBillingEmailPort;
    @Mock private PlatformBillingSmsPort platformBillingSmsPort;

    @Test
    void should_notifyBothChannels_when_invoiceDueInTwoDays() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        LocalDate issuedOn = today.minusDays(5);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), issuedOn, issuedOn.plusMonths(1), issuedOn);

        when(platformInvoiceRepository.findByStatusAndDueDate(PlatformInvoiceStatus.ISSUED, today.plusDays(2))).thenReturn(List.of(invoice));
        when(tenantAdminPort.getOverview(invoice.getTenantId())).thenReturn(overview(invoice.getTenantId()));
        when(tenantAdminPort.findBillingContactEmail(invoice.getTenantId())).thenReturn(Optional.of("admin@klinik.com"));
        when(tenantAdminPort.findBillingContactPhone(invoice.getTenantId())).thenReturn(Optional.of("+905551112233"));

        new RemindDueSoonInvoicesUseCase(platformInvoiceRepository, tenantAdminPort, platformBillingEmailPort, platformBillingSmsPort).execute(today);

        verify(platformBillingEmailPort).sendInvoiceDueSoon(invoice, "Test Klinik", "admin@klinik.com");
        verify(platformBillingSmsPort).sendInvoiceDueSoon(invoice, "Test Klinik", "+905551112233");
    }

    @Test
    void should_skipSmsChannel_when_phoneNotFound() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        LocalDate issuedOn = today.minusDays(5);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), issuedOn, issuedOn.plusMonths(1), issuedOn);

        when(platformInvoiceRepository.findByStatusAndDueDate(PlatformInvoiceStatus.ISSUED, today.plusDays(2))).thenReturn(List.of(invoice));
        when(tenantAdminPort.getOverview(invoice.getTenantId())).thenReturn(overview(invoice.getTenantId()));
        when(tenantAdminPort.findBillingContactEmail(invoice.getTenantId())).thenReturn(Optional.of("admin@klinik.com"));
        when(tenantAdminPort.findBillingContactPhone(invoice.getTenantId())).thenReturn(Optional.empty());

        new RemindDueSoonInvoicesUseCase(platformInvoiceRepository, tenantAdminPort, platformBillingEmailPort, platformBillingSmsPort).execute(today);

        verify(platformBillingSmsPort, never()).sendInvoiceDueSoon(any(), any(), any());
        verify(platformBillingEmailPort).sendInvoiceDueSoon(any(), any(), any());
    }

    private TenantAdminOverview overview(UUID tenantId) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.ACTIVE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 27), 1, 3
        );
    }
}
