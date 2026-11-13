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
    void should_endAfterSevenDays_when_trialStartedWithSevenDays() {
        UUID tenantId = UUID.randomUUID();

        Subscription subscription = Subscription.startTrial(tenantId, java.time.LocalDate.now(), 7);

        assertThat(subscription.getTenantId()).isEqualTo(tenantId);
        assertThat(subscription.getPlanCode()).isEqualTo("TRIAL");
        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.TRIAL);
        assertThat(subscription.getRenewsAt()).isEqualTo(LocalDate.now().plusDays(7));
    }

    @Test
    void should_notBeExpired_when_todayIsRenewalDay() {
        Subscription subscription = Subscription.startTrial(UUID.randomUUID(), java.time.LocalDate.now(), 7);

        assertThat(subscription.isTrialExpired(subscription.getRenewsAt())).isFalse();
    }

    @Test
    void should_beExpired_when_todayIsAfterRenewalDay() {
        Subscription subscription = Subscription.startTrial(UUID.randomUUID(), java.time.LocalDate.now(), 7);

        assertThat(subscription.isTrialExpired(subscription.getRenewsAt().plusDays(1))).isTrue();
    }

    @Test
    void should_neverBeExpired_when_planIsNotTrial() {
        Subscription subscription = Subscription.startPaid(UUID.randomUUID(), "PRO", LocalDate.now().minusDays(30));

        assertThat(subscription.isTrialExpired(LocalDate.now())).isFalse();
    }

    @Test
    void should_becomeActivePaid_when_activatePaidCalled() {
        Subscription subscription = Subscription.startTrial(UUID.randomUUID(), java.time.LocalDate.now(), 7);

        subscription.activatePaid("PRO", LocalDate.now().plusMonths(12), BillingPeriod.ANNUAL);

        assertThat(subscription.getPlanCode()).isEqualTo("PRO");
        assertThat(subscription.getBillingStatus()).isEqualTo(BillingStatus.ACTIVE);
        assertThat(subscription.getBillingPeriod()).isEqualTo(BillingPeriod.ANNUAL);
        assertThat(subscription.isTrialExpired(LocalDate.now().plusYears(5))).isFalse();
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

    @Test
    void should_startAndEndOnGivenDay_when_startTrialCalledWithStartDate() {
        Subscription subscription = Subscription.startTrial(UUID.randomUUID(), LocalDate.of(2026, 11, 14), 7);

        assertThat(subscription.getStartedAt()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(subscription.getRenewsAt()).isEqualTo(LocalDate.of(2026, 11, 21));
    }
}
