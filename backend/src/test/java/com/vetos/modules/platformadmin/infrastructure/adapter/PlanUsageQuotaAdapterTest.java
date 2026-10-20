package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.infrastructure.persistence.MonthlyUsageStore;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.quota.MonthlyQuotaExceededException;
import com.vetos.platform.quota.QuotaProperties;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanUsageQuotaAdapterTest {

    private static final UUID TENANT = UUID.randomUUID();
    // 2026-10-15T09:00Z -> Istanbul 12:00, donem 2026-10
    private static final Clock OCT = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneOffset.UTC);

    private final TenantAdminPort tenantAdminPort = mock(TenantAdminPort.class);
    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final MonthlyUsageStore store = mock(MonthlyUsageStore.class);
    private final QuotaProperties props = new QuotaProperties(new QuotaProperties.Trial(30, 100));

    private PlanUsageQuotaAdapter adapter(boolean enforcement, Clock clock) {
        return new PlanUsageQuotaAdapter(new PlanLookup(tenantAdminPort, planRepository), store, props, enforcement, clock);
    }

    private Plan planWithQuotas(Integer ai, Integer message) {
        Plan plan = Plan.create("PRO", "Pro", BigDecimal.TEN);
        plan.updateQuotas(ai, message);
        return plan;
    }

    @Test
    void should_countButNeverBlock_when_enforcementDisabled() {
        adapter(false, OCT).reserve(TENANT, UsageKind.AI, 1);
        verify(store).increment(TENANT, "2026-10", UsageKind.AI, 1);
        verify(store, never()).tryIncrement(any(), any(), any(), anyInt(), anyInt());
        verifyNoInteractions(tenantAdminPort);
    }

    @Test
    void should_treatNullPlanQuotaAsUnlimited() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWithQuotas(null, 500)));

        adapter(true, OCT).reserve(TENANT, UsageKind.AI, 1);

        verify(store).increment(TENANT, "2026-10", UsageKind.AI, 1);
        verify(store, never()).tryIncrement(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void should_reserveAgainstPlanQuota() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWithQuotas(100, 500)));
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.MESSAGE, 300, 500)).thenReturn(true);

        adapter(true, OCT).reserve(TENANT, UsageKind.MESSAGE, 300);

        verify(store).tryIncrement(TENANT, "2026-10", UsageKind.MESSAGE, 300, 500);
    }

    @Test
    void should_throwWithUsage_when_quotaInsufficient() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWithQuotas(100, 500)));
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.MESSAGE, 300, 500)).thenReturn(false);
        when(store.used(TENANT, "2026-10", UsageKind.MESSAGE)).thenReturn(380);

        assertThatThrownBy(() -> adapter(true, OCT).reserve(TENANT, UsageKind.MESSAGE, 300))
            .isInstanceOf(MonthlyQuotaExceededException.class)
            .hasMessage("Bu ayki mesaj hakkınız yetersiz: 300 alıcı, kalan 120.");
    }

    @Test
    void should_useTrialQuotasFromProperties() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("TRIAL"));
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.AI, 1, 30)).thenReturn(true);

        adapter(true, OCT).reserve(TENANT, UsageKind.AI, 1);

        verify(store).tryIncrement(TENANT, "2026-10", UsageKind.AI, 1, 30);
        verifyNoInteractions(planRepository);
    }

    @Test
    void should_useZeroQuota_when_noSubscription() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.empty());
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.AI, 1, 0)).thenReturn(false);

        assertThatThrownBy(() -> adapter(true, OCT).reserve(TENANT, UsageKind.AI, 1))
            .isInstanceOf(MonthlyQuotaExceededException.class);
    }

    @Test
    void should_useZeroQuota_when_planRowMissing() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("GONE"));
        when(planRepository.findByCode("GONE")).thenReturn(Optional.empty());
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.AI, 1, 0)).thenReturn(false);

        assertThatThrownBy(() -> adapter(true, OCT).reserve(TENANT, UsageKind.AI, 1))
            .isInstanceOf(MonthlyQuotaExceededException.class);
    }

    @Test
    void should_useIstanbulCalendarMonth() {
        Clock lateOctoberUtc = Clock.fixed(Instant.parse("2026-10-31T21:30:00Z"), ZoneOffset.UTC);
        adapter(false, lateOctoberUtc).recordUnconditional(TENANT, UsageKind.MESSAGE, 1);
        verify(store).increment(TENANT, "2026-11", UsageKind.MESSAGE, 1);
    }

    @Test
    void should_doNothing_forNonPositiveAmounts() {
        PlanUsageQuotaAdapter a = adapter(true, OCT);
        a.reserve(TENANT, UsageKind.MESSAGE, 0);
        a.recordUnconditional(TENANT, UsageKind.MESSAGE, 0);
        a.release(TENANT, UsageKind.MESSAGE, 0);
        verifyNoInteractions(store, tenantAdminPort);
    }

    @Test
    void should_releaseViaDecrement() {
        adapter(true, OCT).release(TENANT, UsageKind.AI, 1);
        verify(store).decrement(TENANT, "2026-10", UsageKind.AI, 1);
    }

    @Test
    void should_reportCurrentUsage_withResolvedQuotas() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWithQuotas(100, null)));
        when(store.used(TENANT, "2026-10", UsageKind.AI)).thenReturn(34);
        when(store.used(TENANT, "2026-10", UsageKind.MESSAGE)).thenReturn(1240);

        UsageSnapshot snapshot = adapter(false, OCT).currentUsage(TENANT);

        assertThat(snapshot).isEqualTo(new UsageSnapshot(
            "2026-10", false, new UsageSnapshot.Item(34, 100), new UsageSnapshot.Item(1240, null)
        ));
    }
}
