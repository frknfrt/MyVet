package com.vetos.modules.platformadmin.application.dto;

import java.math.BigDecimal;

public record PlanRevenueBreakdown(String planCode, String planName, int tenantCount, BigDecimal monthlyRevenue) {}
