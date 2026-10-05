package com.vetos.modules.platformadmin.application.dto;

import com.vetos.modules.platformadmin.domain.PlanFeatureFlag;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UpdatePlanCommand(
    UUID planId,
    String name,
    BigDecimal monthlyPrice,
    BigDecimal annualPrice,
    String description,
    String badge,
    String imageUrl,
    List<String> features,
    Set<PlanFeatureFlag> enabledFeatures,
    boolean active
) {}
