package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.Plan;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanResponse(UUID id, String code, String name, BigDecimal monthlyPrice, boolean active) {
    public static PlanResponse from(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getMonthlyPrice(), plan.isActive());
    }
}
