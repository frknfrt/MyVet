package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.feature.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanFeatureAccessAdapterTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlanRepository planRepository;

    private PlanFeatureAccessAdapter adapter(boolean enforcementEnabled) {
        return new PlanFeatureAccessAdapter(new PlanLookup(tenantAdminPort, planRepository), enforcementEnabled);
    }

    private Plan planWith(Set<Feature> features) {
        Plan plan = Plan.create("PRO", "Pro", new BigDecimal("1000"));
        plan.updateDetails("Pro", new BigDecimal("1000"), null, null, null, null, List.of(), features);
        return plan;
    }

    @Test
    void should_enableEverything_when_enforcementDisabled() {
        PlanFeatureAccessAdapter adapter = adapter(false);

        assertThat(adapter.enabledFeatures(TENANT)).isEqualTo(EnumSet.allOf(Feature.class));
        assertThat(adapter.isEnabled(TENANT, Feature.E_FATURA)).isTrue();
        assertThat(adapter.enforcementActive()).isFalse();
        verifyNoInteractions(tenantAdminPort, planRepository);
    }

    @Test
    void should_enableEverything_when_trialPlan() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("TRIAL"));

        assertThat(adapter(true).enabledFeatures(TENANT)).isEqualTo(EnumSet.allOf(Feature.class));
        verifyNoInteractions(planRepository);
    }

    @Test
    void should_returnPlanFlags_when_planExists() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWith(Set.of(Feature.LAB_INTEGRATION, Feature.IMAGING))));
        PlanFeatureAccessAdapter adapter = adapter(true);

        assertThat(adapter.enabledFeatures(TENANT)).containsExactlyInAnyOrder(Feature.LAB_INTEGRATION, Feature.IMAGING);
        assertThat(adapter.isEnabled(TENANT, Feature.LAB_INTEGRATION)).isTrue();
        assertThat(adapter.isEnabled(TENANT, Feature.AI_ASSISTANT)).isFalse();
        assertThat(adapter.enforcementActive()).isTrue();
    }

    @Test
    void should_disableEverything_when_planRowMissing() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("SILINMIS"));
        when(planRepository.findByCode("SILINMIS")).thenReturn(Optional.empty());

        assertThat(adapter(true).enabledFeatures(TENANT)).isEmpty();
    }

    @Test
    void should_disableEverything_when_subscriptionMissing() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.empty());

        assertThat(adapter(true).enabledFeatures(TENANT)).isEmpty();
        verifyNoInteractions(planRepository);
    }
}
