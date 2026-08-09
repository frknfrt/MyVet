package com.vetos.modules.platformadmin.application.dto;

import java.math.BigDecimal;

public record CreatePlanCommand(String code, String name, BigDecimal monthlyPrice) {}
