package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.platform.subscription.SubscriptionAccess;
import com.vetos.platform.subscription.SubscriptionAccessState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionAccessAdapterTest {

    private static final UUID TENANT = UUID.randomUUID();
    /** Istanbul saatiyle 2026-11-13 ogle. */
    private static final Clock NOON = Clock.fixed(Instant.parse("2026-11-13T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    private final SubscriptionJpaRepository repository = mock(SubscriptionJpaRepository.class);

    private SubscriptionAccessAdapter adapter(Clock clock) {
        return new SubscriptionAccessAdapter(repository, clock);
    }

    private void trialEndingOn(LocalDate endsAt) {
        Subscription subscription = Subscription.startTrial(TENANT, 7);
        subscription.changePlan("TRIAL", BillingStatus.TRIAL, endsAt);
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.of(subscription));
    }

    @Test
    void should_reportTrialWithDaysLeft_when_trialNotEnded() {
        trialEndingOn(TODAY.plusDays(3));

        SubscriptionAccess access = adapter(NOON).accessOf(TENANT);

        assertThat(access.state()).isEqualTo(SubscriptionAccessState.TRIAL);
        assertThat(access.trialEndsAt()).isEqualTo(TODAY.plusDays(3));
        assertThat(access.daysLeft()).isEqualTo(3);
    }

    @Test
    void should_reportTrial_when_todayIsLastDay() {
        trialEndingOn(TODAY);

        SubscriptionAccess access = adapter(NOON).accessOf(TENANT);

        assertThat(access.state()).isEqualTo(SubscriptionAccessState.TRIAL);
        assertThat(access.daysLeft()).isZero();
    }

    @Test
    void should_reportExpired_when_istanbulDateIsPastRenewal() {
        trialEndingOn(TODAY);
        // UTC saatiyle hala 13 Kasim 21:30; Istanbul (UTC+3) icin 14 Kasim 00:30.
        Clock justAfterIstanbulMidnight = Clock.fixed(Instant.parse("2026-11-13T21:30:00Z"), ZoneOffset.UTC);

        SubscriptionAccess access = adapter(justAfterIstanbulMidnight).accessOf(TENANT);

        assertThat(access.state()).isEqualTo(SubscriptionAccessState.TRIAL_EXPIRED);
        assertThat(access.trialEndsAt()).isEqualTo(TODAY);
        assertThat(access.daysLeft()).isZero();
    }

    @Test
    void should_reportInternal_when_planIsInternal() {
        Subscription subscription = Subscription.startTrial(TENANT, 7);
        subscription.changePlan("INTERNAL", BillingStatus.ACTIVE, null);
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.of(subscription));

        SubscriptionAccess access = adapter(NOON).accessOf(TENANT);

        assertThat(access.state()).isEqualTo(SubscriptionAccessState.INTERNAL);
        assertThat(access.trialEndsAt()).isNull();
        assertThat(access.daysLeft()).isNull();
    }

    @Test
    void should_reportActive_when_planIsPaid() {
        when(repository.findByTenantId(TENANT))
            .thenReturn(Optional.of(Subscription.startPaid(TENANT, "PRO", TODAY.minusDays(40))));

        assertThat(adapter(NOON).accessOf(TENANT).state()).isEqualTo(SubscriptionAccessState.ACTIVE);
    }

    @Test
    void should_reportActive_when_noSubscriptionExists() {
        when(repository.findByTenantId(TENANT)).thenReturn(Optional.empty());

        assertThat(adapter(NOON).accessOf(TENANT).state()).isEqualTo(SubscriptionAccessState.ACTIVE);
    }

    @Test
    void should_reloadFromDatabase_when_evicted() {
        trialEndingOn(TODAY.minusDays(1));
        SubscriptionAccessAdapter adapter = adapter(NOON);
        assertThat(adapter.accessOf(TENANT).state()).isEqualTo(SubscriptionAccessState.TRIAL_EXPIRED);
        adapter.accessOf(TENANT); // onbellekten gelir

        when(repository.findByTenantId(TENANT))
            .thenReturn(Optional.of(Subscription.startPaid(TENANT, "PRO", TODAY.plusMonths(1))));
        adapter.evict(TENANT);

        assertThat(adapter.accessOf(TENANT).state()).isEqualTo(SubscriptionAccessState.ACTIVE);
        verify(repository, times(2)).findByTenantId(TENANT);
    }
}
