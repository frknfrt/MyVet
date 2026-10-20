package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.application.DeletePlanUseCase;
import com.vetos.modules.platformadmin.application.RecordAuditLogUseCase;
import com.vetos.modules.platformadmin.application.UpdateTenantSubscriptionUseCase;
import com.vetos.modules.platformadmin.application.dto.UpdateTenantSubscriptionCommand;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gercek CacheManager + gercek islem yoneticisi; veri kaynaklari sahte. Her test kendi rastgele
 * kiraci kimligini / paket kodunu kullanir (onbellek baglam boyunca paylasilir).
 * Canli docker-compose Postgres gerekir.
 */
@SpringBootTest
class PlanLookupCacheTest {

    @MockitoBean private TenantAdminPort tenantAdminPort;
    @MockitoBean private PlanRepository planRepository;
    @MockitoBean private RecordAuditLogUseCase recordAuditLogUseCase;

    @Autowired private PlanLookup planLookup;
    @Autowired private UpdateTenantSubscriptionUseCase updateTenantSubscriptionUseCase;
    @Autowired private DeletePlanUseCase deletePlanUseCase;
    @Autowired private PlatformTransactionManager transactionManager;

    private static final UUID ADMIN = UUID.randomUUID();

    private void changeSubscription(UUID tenantId, String planCode) {
        updateTenantSubscriptionUseCase.execute(
            new UpdateTenantSubscriptionCommand(tenantId, planCode, BillingStatus.ACTIVE, LocalDate.of(2027, 1, 1)),
            ADMIN, "admin@test.local"
        );
    }

    private Plan planWithAiQuota(String code, int aiQuota) {
        Plan plan = Plan.create(code, code, BigDecimal.TEN);
        plan.updateQuotas(aiQuota, null);
        return plan;
    }

    @Test
    void should_keepTenantsApart_and_hitCacheOnSecondRead() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(tenantAdminPort.findPlanCode(a)).thenReturn(Optional.of("PRO"));
        when(tenantAdminPort.findPlanCode(b)).thenReturn(Optional.of("BASIC"));

        assertThat(planLookup.planCodeOf(a)).contains("PRO");
        assertThat(planLookup.planCodeOf(b)).contains("BASIC");
        assertThat(planLookup.planCodeOf(a)).contains("PRO");
        assertThat(planLookup.planCodeOf(b)).contains("BASIC");

        verify(tenantAdminPort, times(1)).findPlanCode(a);
        verify(tenantAdminPort, times(1)).findPlanCode(b);
    }

    @Test
    void should_evictTenantPlanCode_onlyAfterSubscriptionCommit() {
        UUID tenant = UUID.randomUUID();
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("PRO"));
        assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("BASIC"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            changeSubscription(tenant, "BASIC");
            // Commit olmadi: temizlik ertelendi, eski deger hala onbellekte.
            assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
        });

        assertThat(planLookup.planCodeOf(tenant)).contains("BASIC");
    }

    @Test
    void should_keepCachedValue_when_subscriptionChangeRollsBack() {
        UUID tenant = UUID.randomUUID();
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("PRO"));
        assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("BASIC"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            changeSubscription(tenant, "BASIC");
            status.setRollbackOnly();
        });

        assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
    }

    @Test
    void should_notCacheMissingSubscription() {
        UUID tenant = UUID.randomUUID();
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.empty());
        assertThat(planLookup.planCodeOf(tenant)).isEmpty();

        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("PRO"));
        assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
    }

    @Test
    void should_notCacheMissingPlan() {
        String code = "P-" + UUID.randomUUID();
        when(planRepository.findByCode(code)).thenReturn(Optional.empty());
        assertThat(planLookup.planByCode(code)).isEmpty();

        when(planRepository.findByCode(code)).thenReturn(Optional.of(planWithAiQuota(code, 100)));
        assertThat(planLookup.planByCode(code)).map(PlanSnapshot::monthlyAiQuota).contains(100);
    }

    @Test
    void should_clearAllPlans_afterPlanDeleteCommit() {
        String code = "P-" + UUID.randomUUID();
        when(planRepository.findByCode(code)).thenReturn(Optional.of(planWithAiQuota(code, 100)));
        assertThat(planLookup.planByCode(code)).map(PlanSnapshot::monthlyAiQuota).contains(100);
        when(planRepository.findByCode(code)).thenReturn(Optional.of(planWithAiQuota(code, 200)));

        UUID otherPlanId = UUID.randomUUID();
        when(planRepository.findById(otherPlanId)).thenReturn(Optional.of(planWithAiQuota("BASKA", 1)));
        deletePlanUseCase.execute(otherPlanId, ADMIN, "admin@test.local");

        assertThat(planLookup.planByCode(code)).map(PlanSnapshot::monthlyAiQuota).contains(200);
    }

    @Test
    void should_snapshotBeImmutable_and_keepNullQuota() {
        String code = "P-" + UUID.randomUUID();
        Plan plan = Plan.create(code, code, BigDecimal.TEN);
        plan.updateQuotas(null, 500);
        when(planRepository.findByCode(code)).thenReturn(Optional.of(plan));

        PlanSnapshot snapshot = planLookup.planByCode(code).orElseThrow();

        assertThat(snapshot.monthlyAiQuota()).isNull();
        assertThat(snapshot.monthlyMessageQuota()).isEqualTo(500);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> snapshot.enabledFeatures().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
