package com.vetos.modules.platformadmin.application.dto;

import java.math.BigDecimal;
import java.util.List;
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
    boolean active
) {}
