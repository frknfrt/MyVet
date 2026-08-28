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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagOverdueAndSuspendUseCaseTest {

    @Mock private PlatformInvoiceRepository platformInvoiceRepository;
    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlatformBillingEmailPort platformBillingEmailPort;
    @Mock private PlatformBillingSmsPort platformBillingSmsPort;

    @Test
    void should_flagOverdueAndSuspendTenantAndNotify_when_dueDatePassed() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        LocalDate issuedOn = today.minusDays(8);
        PlatformInvoice invoice = PlatformInvoice.issue(UUID.randomUUID(), "PRO", new BigDecimal("500.00"), issuedOn, issuedOn.plusMonths(1), issuedOn);
        UUID tenantId = invoice.getTenantId();

        when(platformInvoiceRepository.findByStatusAndDueDateBefore(PlatformInvoiceStatus.ISSUED, today)).thenReturn(List.of(invoice));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.of("admin@klinik.com"));
        when(tenantAdminPort.findBillingContactPhone(tenantId)).thenReturn(Optional.of("+905551112233"));

        new FlagOverdueAndSuspendUseCase(platformInvoiceRepository, tenantAdminPort, platformBillingEmailPort, platformBillingSmsPort).execute(today);

        assertThat(invoice.getStatus()).isEqualTo(PlatformInvoiceStatus.OVERDUE);
        verify(platformInvoiceRepository).save(invoice);
        verify(tenantAdminPort).updateBillingStatus(tenantId, BillingStatus.PAST_DUE);
        verify(tenantAdminPort).suspend(tenantId);
        verify(platformBillingEmailPort).sendTenantSuspended("Test Klinik", "admin@klinik.com");
        verify(platformBillingSmsPort).sendTenantSuspended("Test Klinik", "+905551112233");
    }

    @Test
    void should_doNothing_when_noInvoicesPastDueDate() {
        LocalDate today = LocalDate.of(2026, 8, 28);
        when(platformInvoiceRepository.findByStatusAndDueDateBefore(PlatformInvoiceStatus.ISSUED, today)).thenReturn(List.of());

        new FlagOverdueAndSuspendUseCase(platformInvoiceRepository, tenantAdminPort, platformBillingEmailPort, platformBillingSmsPort).execute(today);

        verify(tenantAdminPort, never()).suspend(any());
    }

    private TenantAdminOverview overview(UUID tenantId) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.ACTIVE,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 20), 1, 3
        );
    }
}
