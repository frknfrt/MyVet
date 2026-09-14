package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;

public record TodaySalesSummary(BigDecimal totalAmount, int saleCount) {}
