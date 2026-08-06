package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.ProductSalesLine;

import java.math.BigDecimal;

public record ProductSalesLineResponse(String description, int totalQuantity, BigDecimal totalRevenue) {
    public static ProductSalesLineResponse from(ProductSalesLine l) {
        return new ProductSalesLineResponse(l.description(), l.totalQuantity(), l.totalRevenue());
    }
}
