package com.vetos.modules.platformadmin.api.dto;

import com.vetos.platform.feature.Feature;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record UpdatePlanRequest(
    @NotBlank String name,
    @NotNull @DecimalMin("0.0") BigDecimal monthlyPrice,
    @DecimalMin("0.0") BigDecimal annualPrice,
    String description,
    String badge,
    String imageUrl,
    List<String> features,
    Set<Feature> enabledFeatures,
    boolean active,
    @PositiveOrZero Integer monthlyAiQuota,
    @PositiveOrZero Integer monthlyMessageQuota
) {}
