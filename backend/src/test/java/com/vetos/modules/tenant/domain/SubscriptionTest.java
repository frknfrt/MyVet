package com.vetos.modules.tenant.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTest {

    @Test
    void should_updateRenewsAt_when_advanceRenewalCalled() {
        Subscription subscription = Subscription.startPaid(UUID.randomUUID(), "PRO", LocalDate.of(2026, 9, 1));
        LocalDate nextPeriod = LocalDate.of(2026, 9, 28);

        subscription.advanceRenewal(nextPeriod);

        assertThat(subscription.getRenewsAt()).isEqualTo(nextPeriod);
    }

    @Test
    void should_updateBillingStatusOnly_when_updateBillingStatusCalled() {
        Subscription subscription = Subscription.startPaid(UUID.randomUUID(), "PRO", LocalDate.of(2026, 9, 1));
        String originalPlanCode = subscription.getPlanCode();

        subscription.updateBillingStatus(BillingStatus.PAST_DUE);

        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.PAST_DUE);
        assertThat(subscription.getPlanCode()).isEqualTo(originalPlanCode);
    }

    @Test
    void should_startActiveWithChosenPlan_when_startPaidCalled() {
        UUID tenantId = UUID.randomUUID();
        LocalDate renewsAt = LocalDate.of(2026, 9, 30);

        Subscription subscription = Subscription.startPaid(tenantId, "PRO", renewsAt);

        assertThat(subscription.getTenantId()).isEqualTo(tenantId);
        assertThat(subscription.getPlanCode()).isEqualTo("PRO");
        assertThat(subscription.getRenewsAt()).isEqualTo(renewsAt);
        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.ACTIVE);
    }

    @Test
    void should_startTrialWithFourteenDayPeriod_when_startTrialCalled() {
        UUID tenantId = UUID.randomUUID();

        Subscription subscription = Subscription.startTrial(tenantId);

        assertThat(subscription.getTenantId()).isEqualTo(tenantId);
        assertThat(subscription.getPlanCode()).isEqualTo("TRIAL");
        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.TRIAL);
        assertThat(subscription.getRenewsAt()).isEqualTo(subscription.getStartedAt().plusDays(14));
    }

    @Test
    void should_setBillingStatusCanceled_butKeepRenewsAt_when_cancelCalled() {
        LocalDate renewsAt = LocalDate.of(2026, 9, 30);
        Subscription subscription = Subscription.startPaid(UUID.randomUUID(), "PRO", renewsAt);

        subscription.cancel();

        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.CANCELED);
        assertThat(subscription.getRenewsAt()).isEqualTo(renewsAt);
    }

    @Test
    void should_setBillingStatusActive_when_reactivateCancellationCalled() {
        Subscription subscription = Subscription.startPaid(UUID.randomUUID(), "PRO", LocalDate.of(2026, 9, 30));
        subscription.cancel();

        subscription.reactivateCancellation();

        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.ACTIVE);
    }
}
