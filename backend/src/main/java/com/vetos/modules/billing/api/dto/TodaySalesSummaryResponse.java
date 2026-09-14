package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.TodaySalesSummary;

import java.math.BigDecimal;

public record TodaySalesSummaryResponse(BigDecimal totalAmount, int saleCount) {
    public static TodaySalesSummaryResponse from(TodaySalesSummary summary) {
        return new TodaySalesSummaryResponse(summary.totalAmount(), summary.saleCount());
    }
}
