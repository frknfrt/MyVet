package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;

public record ProductSalesLine(String description, int totalQuantity, BigDecimal totalRevenue) {}
