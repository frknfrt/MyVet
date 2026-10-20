package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.infrastructure.persistence.MonthlyUsageStore;
import com.vetos.platform.quota.MonthlyQuotaExceededException;
import com.vetos.platform.quota.QuotaProperties;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.quota.UsageSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Paket kotasi cozumu (spec 2026-10-15 §2.4) -- sira PlanFeatureAccessAdapter ile ayni:
 * kisitlama kapali -> say ama engelleme; abonelik yok -> 0; TRIAL -> yml; plan -> sutun
 * (null = sinirsiz); plan satiri yok -> 0. Onbellek: PlanLookup (5 dk; admin degisikligi commit sonrasi temizler).
 */
@Component
@Slf4j
class PlanUsageQuotaAdapter implements UsageQuotaPort {

    static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PlanLookup planLookup;
    private final MonthlyUsageStore store;
    private final QuotaProperties properties;
    private final boolean enforcementEnabled;
    private final Clock clock;

    @Autowired
    PlanUsageQuotaAdapter(
        PlanLookup planLookup,
        MonthlyUsageStore store,
        QuotaProperties properties,
        @Value("${features.enforcement.enabled:false}") boolean enforcementEnabled
    ) {
        this(planLookup, store, properties, enforcementEnabled, Clock.system(ZONE));
    }

    PlanUsageQuotaAdapter(
        PlanLookup planLookup,
        MonthlyUsageStore store,
        QuotaProperties properties,
        boolean enforcementEnabled,
        Clock clock
    ) {
        this.planLookup = planLookup;
        this.store = store;
        this.properties = properties;
        this.enforcementEnabled = enforcementEnabled;
        this.clock = clock;
    }

    @Override
    public void reserve(UUID tenantId, UsageKind kind, int amount) {
        if (amount <= 0) {
            return;
        }
        String period = currentPeriod();
        if (!enforcementEnabled) {
            store.increment(tenantId, period, kind, amount);
            return;
        }
        Integer quota = resolveQuota(tenantId, kind);
        if (quota == null) {
            store.increment(tenantId, period, kind, amount);
            return;
        }
        if (!store.tryIncrement(tenantId, period, kind, amount, quota)) {
            throw new MonthlyQuotaExceededException(kind, store.used(tenantId, period, kind), quota, amount);
        }
    }

    @Override
    public void recordUnconditional(UUID tenantId, UsageKind kind, int amount) {
        if (amount <= 0) {
            return;
        }
        store.increment(tenantId, currentPeriod(), kind, amount);
    }

    @Override
    public void release(UUID tenantId, UsageKind kind, int amount) {
        if (amount <= 0) {
            return;
        }
        store.decrement(tenantId, currentPeriod(), kind, amount);
    }

    @Override
    public UsageSnapshot currentUsage(UUID tenantId) {
        String period = currentPeriod();
        return new UsageSnapshot(
            period,
            enforcementEnabled,
            new UsageSnapshot.Item(store.used(tenantId, period, UsageKind.AI), resolveQuota(tenantId, UsageKind.AI)),
            new UsageSnapshot.Item(store.used(tenantId, period, UsageKind.MESSAGE), resolveQuota(tenantId, UsageKind.MESSAGE))
        );
    }

    /** null = sinirsiz. Optional.map KULLANMA: null kota bos Optional'a donup "plan yok -> 0"a duser. */
    private Integer resolveQuota(UUID tenantId, UsageKind kind) {
        Optional<String> planCode = planLookup.planCodeOf(tenantId);
        if (planCode.isEmpty()) {
            log.warn("Abonelik bulunamadi, kota 0: tenantId={}", tenantId);
            return 0;
        }
        if (PlanFeatureAccessAdapter.TRIAL_PLAN_CODE.equals(planCode.get())) {
            return kind == UsageKind.AI ? properties.trial().ai() : properties.trial().message();
        }
        Optional<PlanSnapshot> plan = planLookup.planByCode(planCode.get());
        if (plan.isEmpty()) {
            log.warn("Plan bulunamadi, kota 0: tenantId={}, planCode={}", tenantId, planCode.get());
            return 0;
        }
        return kind == UsageKind.AI ? plan.get().monthlyAiQuota() : plan.get().monthlyMessageQuota();
    }

    private String currentPeriod() {
        return YearMonth.now(clock.withZone(ZONE)).format(PERIOD_FORMAT);
    }
}
