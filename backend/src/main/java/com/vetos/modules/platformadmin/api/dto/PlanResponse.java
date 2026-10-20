package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.platform.feature.Feature;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PlanResponse(
    UUID id,
    String code,
    String name,
    BigDecimal monthlyPrice,
    BigDecimal annualPrice,
    String description,
    String badge,
    String imageUrl,
    List<String> features,
    Set<Feature> enabledFeatures,
    boolean active,
    Integer monthlyAiQuota,
    Integer monthlyMessageQuota
) {
    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
            plan.getId(),
            plan.getCode(),
            plan.getName(),
            plan.getMonthlyPrice(),
            plan.getAnnualPrice(),
            plan.getDescription(),
            plan.getBadge(),
            plan.getImageUrl(),
            plan.getFeatures(),
            plan.getEnabledFeatures(),
            plan.isActive(),
            plan.getMonthlyAiQuota(),
            plan.getMonthlyMessageQuota()
        );
    }
}
