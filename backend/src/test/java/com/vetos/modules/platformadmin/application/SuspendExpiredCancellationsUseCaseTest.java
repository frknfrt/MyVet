package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.tenant.domain.BillableSubscription;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import com.vetos.modules.tenant.domain.TenantSuspensionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuspendExpiredCancellationsUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlatformBillingEmailPort platformBillingEmailPort;
    @Mock private PlatformBillingSmsPort platformBillingSmsPort;

    private SuspendExpiredCancellationsUseCase useCase() {
        return new SuspendExpiredCancellationsUseCase(tenantAdminPort, platformBillingEmailPort, platformBillingSmsPort);
    }

    @Test
    void should_suspendAndNotify_when_canceledSubscriptionPeriodEnded() {
        LocalDate today = LocalDate.of(2026, 11, 5);
        UUID tenantId = UUID.randomUUID();
        BillableSubscription subscription = new BillableSubscription(tenantId, "PRO", today.minusDays(1));
        when(tenantAdminPort.listCanceledSubscriptionsDueOnOrBefore(today)).thenReturn(List.of(subscription));
        when(tenantAdminPort.getOverview(tenantId)).thenReturn(overview(tenantId));
        when(tenantAdminPort.findBillingContactEmail(tenantId)).thenReturn(Optional.of("admin@klinik.com"));
        when(tenantAdminPort.findBillingContactPhone(tenantId)).thenReturn(Optional.of("+905551112233"));

        useCase().execute(today);

        verify(tenantAdminPort).suspend(tenantId, TenantSuspensionReason.SELF_CANCELLED, null);
        verify(platformBillingEmailPort).sendTenantSuspended("Test Klinik", "admin@klinik.com");
        verify(platformBillingSmsPort).sendTenantSuspended("Test Klinik", "+905551112233");
    }

    @Test
    void should_doNothing_when_noExpiredCancellations() {
        LocalDate today = LocalDate.of(2026, 11, 5);
        when(tenantAdminPort.listCanceledSubscriptionsDueOnOrBefore(today)).thenReturn(List.of());

        useCase().execute(today);

        verify(tenantAdminPort, never()).suspend(any(), any(), any());
    }

    private TenantAdminOverview overview(UUID tenantId) {
        return new TenantAdminOverview(
            tenantId, "Test Klinik", "123", TenantStatus.ACTIVE, Instant.now(), "PRO", BillingStatus.CANCELED,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 4), 1, 3, null, null
        );
    }
}
