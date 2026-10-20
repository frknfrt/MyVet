package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.platform.feature.Feature;

import java.util.Set;

/**
 * Paketin onbellege konan sade, degistirilemez kopyasi (spec 2026-10-18 §3). Entity onbellege
 * konmaz. Kotada null = sinirsiz (bugunku anlam aynen).
 */
public record PlanSnapshot(String code, Set<Feature> enabledFeatures, Integer monthlyAiQuota, Integer monthlyMessageQuota) {

    public PlanSnapshot {
        enabledFeatures = Set.copyOf(enabledFeatures);
    }

    static PlanSnapshot of(Plan plan) {
        return new PlanSnapshot(plan.getCode(), plan.getEnabledFeatures(), plan.getMonthlyAiQuota(), plan.getMonthlyMessageQuota());
    }
}
