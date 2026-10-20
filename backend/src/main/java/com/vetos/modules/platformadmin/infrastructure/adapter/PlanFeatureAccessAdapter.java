package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Kiracinin paketinden acik modulleri okur (spec 2026-10-12 plan ozellik kisitlamasi):
 * ayar kapali -> hepsi acik; TRIAL -> hepsi acik; paket bulunursa "Modul Erisimi" isaretleri;
 * abonelik/paket bulunamazsa hepsi kapali (ucretli ozellik yanlislikla acilmasin) + uyari.
 * Onbellek: PlanLookup (5 dk; admin degisikligi commit sonrasi temizler).
 */
@Component
@Slf4j
class PlanFeatureAccessAdapter implements FeatureAccessPort {

    static final String TRIAL_PLAN_CODE = "TRIAL";

    private final PlanLookup planLookup;
    private final boolean enforcementEnabled;

    PlanFeatureAccessAdapter(
        PlanLookup planLookup,
        @Value("${features.enforcement.enabled:false}") boolean enforcementEnabled
    ) {
        this.planLookup = planLookup;
        this.enforcementEnabled = enforcementEnabled;
    }

    @Override
    public boolean isEnabled(UUID tenantId, Feature feature) {
        return enabledFeatures(tenantId).contains(feature);
    }

    @Override
    public Set<Feature> enabledFeatures(UUID tenantId) {
        if (!enforcementEnabled) {
            return EnumSet.allOf(Feature.class);
        }
        Optional<String> planCode = planLookup.planCodeOf(tenantId);
        if (planCode.isEmpty()) {
            log.warn("Abonelik bulunamadi, tum ozellikler kapali: tenantId={}", tenantId);
            return EnumSet.noneOf(Feature.class);
        }
        if (TRIAL_PLAN_CODE.equals(planCode.get())) {
            return EnumSet.allOf(Feature.class);
        }
        return planLookup.planByCode(planCode.get())
            .map(PlanSnapshot::enabledFeatures)
            .orElseGet(() -> {
                log.warn("Plan bulunamadi, tum ozellikler kapali: tenantId={}, planCode={}", tenantId, planCode.get());
                return EnumSet.noneOf(Feature.class);
            });
    }

    @Override
    public boolean enforcementActive() {
        return enforcementEnabled;
    }
}
