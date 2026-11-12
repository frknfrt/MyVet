package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.platform.subscription.ReservedPlanCodes;
import com.vetos.platform.branch.BranchLimitPort;
import com.vetos.platform.quota.QuotaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Kiracinin paketinden azami sube sayisini okur (spec 2026-11-04) -- sira PlanUsageQuotaAdapter
 * ile ayni: kisitlama kapali -> sinirsiz; TRIAL -> yml; plan -> sutun (null = sinirsiz); plan
 * satiri/abonelik yok -> 0. Onbellek: PlanLookup (5 dk).
 */
@Component
@Slf4j
class PlanBranchLimitAdapter implements BranchLimitPort {

    private final PlanLookup planLookup;
    private final boolean enforcementEnabled;
    private final QuotaProperties properties;

    PlanBranchLimitAdapter(
        PlanLookup planLookup,
        @Value("${features.enforcement.enabled:false}") boolean enforcementEnabled,
        QuotaProperties properties
    ) {
        this.planLookup = planLookup;
        this.enforcementEnabled = enforcementEnabled;
        this.properties = properties;
    }

    @Override
    public Integer maxBranchesFor(UUID tenantId) {
        if (!enforcementEnabled) {
            return null;
        }
        Optional<String> planCode = planLookup.planCodeOf(tenantId);
        if (planCode.isEmpty()) {
            log.warn("Abonelik bulunamadi, sube siniri 0: tenantId={}", tenantId);
            return 0;
        }
        if (ReservedPlanCodes.INTERNAL.equals(planCode.get())) {
            return null; // test ekibi hesabi: sinirsiz
        }
        if (PlanFeatureAccessAdapter.TRIAL_PLAN_CODE.equals(planCode.get())) {
            return properties.trial().maxBranches();
        }
        return planLookup.planByCode(planCode.get())
            .map(PlanSnapshot::maxBranches)
            .orElseGet(() -> {
                log.warn("Plan bulunamadi, sube siniri 0: tenantId={}, planCode={}", tenantId, planCode.get());
                return 0;
            });
    }
}
