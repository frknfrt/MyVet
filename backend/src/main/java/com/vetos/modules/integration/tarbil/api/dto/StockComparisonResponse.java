package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.StockComparison;

public record StockComparisonResponse(int newCount, int quantityDiffersCount, int matchedCount) {
    public static StockComparisonResponse from(StockComparison c) {
        return new StockComparisonResponse(c.newCount(), c.quantityDiffersCount(), c.matchedCount());
    }
}
