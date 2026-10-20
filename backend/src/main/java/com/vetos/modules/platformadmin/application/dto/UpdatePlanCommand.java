package com.vetos.modules.platformadmin.application.dto;

import com.vetos.platform.feature.Feature;

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
    Set<Feature> enabledFeatures,
    boolean active,
    Integer monthlyAiQuota,
    Integer monthlyMessageQuota
) {}
