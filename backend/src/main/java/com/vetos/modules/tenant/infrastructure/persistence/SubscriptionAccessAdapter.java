package com.vetos.modules.tenant.infrastructure.persistence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.platform.subscription.ReservedPlanCodes;
import com.vetos.platform.subscription.SubscriptionAccess;
import com.vetos.platform.subscription.SubscriptionAccessPort;
import com.vetos.platform.subscription.SubscriptionAccessState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Onbellek kiraci basina 60 sn: kapi her istekte cagrilir, veritabanina her istekte
 * gitmesin. Abonelik degisince evict edilir (ayni instance); baska instance en gec
 * 60 sn sonra gorur.
 */
@Component
class SubscriptionAccessAdapter implements SubscriptionAccessPort {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final SubscriptionJpaRepository subscriptionJpaRepository;
    private final Clock clock;
    private final Cache<UUID, SubscriptionAccess> cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(60))
        .maximumSize(10_000)
        .build();

    @Autowired
    SubscriptionAccessAdapter(SubscriptionJpaRepository subscriptionJpaRepository) {
        this(subscriptionJpaRepository, Clock.systemUTC());
    }

    SubscriptionAccessAdapter(SubscriptionJpaRepository subscriptionJpaRepository, Clock clock) {
        this.subscriptionJpaRepository = subscriptionJpaRepository;
        this.clock = clock;
    }

    @Override
    public SubscriptionAccess accessOf(UUID tenantId) {
        return cache.get(tenantId, this::load);
    }

    @Override
    public void evict(UUID tenantId) {
        cache.invalidate(tenantId);
    }

    private SubscriptionAccess load(UUID tenantId) {
        return subscriptionJpaRepository.findByTenantId(tenantId)
            .map(this::toAccess)
            .orElseGet(() -> SubscriptionAccess.of(SubscriptionAccessState.ACTIVE));
    }

    private SubscriptionAccess toAccess(Subscription subscription) {
        String planCode = subscription.getPlanCode();
        if (ReservedPlanCodes.INTERNAL.equals(planCode)) {
            return SubscriptionAccess.of(SubscriptionAccessState.INTERNAL);
        }
        if (!ReservedPlanCodes.TRIAL.equals(planCode)) {
            return SubscriptionAccess.of(SubscriptionAccessState.ACTIVE);
        }
        LocalDate today = LocalDate.now(clock.withZone(ISTANBUL));
        LocalDate endsAt = subscription.getRenewsAt();
        if (subscription.isTrialExpired(today)) {
            return new SubscriptionAccess(SubscriptionAccessState.TRIAL_EXPIRED, endsAt, 0);
        }
        Integer daysLeft = endsAt == null ? null : (int) Math.max(0, ChronoUnit.DAYS.between(today, endsAt));
        return new SubscriptionAccess(SubscriptionAccessState.TRIAL, endsAt, daysLeft);
    }
}
