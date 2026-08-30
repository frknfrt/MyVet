package com.vetos.modules.tenant.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionTest {

    @Test
    void should_updateRenewsAt_when_advanceRenewalCalled() {
        Subscription subscription = Subscription.startTrial(UUID.randomUUID());
        LocalDate nextPeriod = LocalDate.of(2026, 9, 28);

        subscription.advanceRenewal(nextPeriod);

        assertThat(subscription.getRenewsAt()).isEqualTo(nextPeriod);
    }

    @Test
    void should_updateBillingStatusOnly_when_updateBillingStatusCalled() {
        Subscription subscription = Subscription.startTrial(UUID.randomUUID());
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
}
