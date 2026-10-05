package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.application.dto.PlanRevenueBreakdown;

import java.math.BigDecimal;

public record PlanRevenueBreakdownResponse(String planCode, String planName, int tenantCount, BigDecimal monthlyRevenue) {
    public static PlanRevenueBreakdownResponse from(PlanRevenueBreakdown b) {
        return new PlanRevenueBreakdownResponse(b.planCode(), b.planName(), b.tenantCount(), b.monthlyRevenue());
    }
}
