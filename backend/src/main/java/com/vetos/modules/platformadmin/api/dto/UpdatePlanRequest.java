package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record UpdatePlanRequest(
    @NotBlank String name,
    @NotNull @DecimalMin("0.0") BigDecimal monthlyPrice,
    @DecimalMin("0.0") BigDecimal annualPrice,
    String description,
    String badge,
    String imageUrl,
    List<String> features,
    boolean active
) {}
