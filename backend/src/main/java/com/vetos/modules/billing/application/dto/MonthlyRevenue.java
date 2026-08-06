package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;

public record MonthlyRevenue(String month, BigDecimal revenue) {}
