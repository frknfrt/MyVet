package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.platform.subscription.ReservedPlanCodes;
import com.vetos.platform.quota.QuotaProperties;
import com.vetos.platform.staff.StaffUserLimitPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Kiracinin paketinden azami personel sayisini okur (spec 2026-11-05) -- sira PlanBranchLimitAdapter
 * ile birebir ayni: kisitlama kapali -> sinirsiz; TRIAL -> yml; plan -> sutun (null = sinirsiz); plan
 * satiri/abonelik yok -> 0. Onbellek: PlanLookup (5 dk).
 */
@Component
@Slf4j
class PlanStaffUserLimitAdapter implements StaffUserLimitPort {

    private final PlanLookup planLookup;
    private final boolean enforcementEnabled;
    private final QuotaProperties properties;

    PlanStaffUserLimitAdapter(
        PlanLookup planLookup,
        @Value("${features.enforcement.enabled:false}") boolean enforcementEnabled,
        QuotaProperties properties
    ) {
        this.planLookup = planLookup;
        this.enforcementEnabled = enforcementEnabled;
        this.properties = properties;
    }

    @Override
    public Integer maxStaffUsersFor(UUID tenantId) {
        if (!enforcementEnabled) {
            return null;
        }
        Optional<String> planCode = planLookup.planCodeOf(tenantId);
        if (planCode.isEmpty()) {
            log.warn("Abonelik bulunamadi, personel siniri 0: tenantId={}", tenantId);
            return 0;
        }
        if (ReservedPlanCodes.INTERNAL.equals(planCode.get())) {
            return null; // test ekibi hesabi: sinirsiz
        }
        if (PlanFeatureAccessAdapter.TRIAL_PLAN_CODE.equals(planCode.get())) {
            return properties.trial().maxStaffUsers();
        }
        return planLookup.planByCode(planCode.get())
            .map(PlanSnapshot::maxStaffUsers)
            .orElseGet(() -> {
                log.warn("Plan bulunamadi, personel siniri 0: tenantId={}, planCode={}", tenantId, planCode.get());
                return 0;
            });
    }
}
