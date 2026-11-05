package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.modules.tenant.domain.SubscriptionRepository;
import com.vetos.modules.tenant.domain.exception.SubscriptionNotCancellableConflictException;
import com.vetos.modules.tenant.domain.exception.SubscriptionNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelSubscriptionUseCaseTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private AuditPort auditPort;

    private final UUID tenantId = UUID.randomUUID();

    private CancelSubscriptionUseCase useCase() {
        return new CancelSubscriptionUseCase(subscriptionRepository, auditPort);
    }

    @Test
    void should_cancelAndRecordAudit_when_subscriptionIsActive() {
        LocalDate renewsAt = LocalDate.of(2026, 12, 1);
        Subscription subscription = Subscription.startPaid(tenantId, "PRO", renewsAt);
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));

        var result = useCase().execute(tenantId);

        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.CANCELED);
        assertThat(result.billingStatus()).isEqualTo(BillingStatus.CANCELED);
        assertThat(result.renewsAt()).isEqualTo(renewsAt);
        verify(subscriptionRepository).save(subscription);
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.SUBSCRIPTION_CANCELLED);
    }

    @Test
    void should_reject_when_subscriptionIsTrial() {
        Subscription subscription = Subscription.startTrial(tenantId);
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> useCase().execute(tenantId))
            .isInstanceOf(SubscriptionNotCancellableConflictException.class);
        verifyNoInteractions(auditPort);
    }

    @Test
    void should_reject_when_subscriptionAlreadyCanceled() {
        Subscription subscription = Subscription.startPaid(tenantId, "PRO", LocalDate.of(2026, 12, 1));
        subscription.cancel();
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> useCase().execute(tenantId))
            .isInstanceOf(SubscriptionNotCancellableConflictException.class);
    }

    @Test
    void should_throwNotFound_when_noSubscriptionForTenant() {
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(tenantId)).isInstanceOf(SubscriptionNotFoundException.class);
        verifyNoInteractions(auditPort);
    }
}
