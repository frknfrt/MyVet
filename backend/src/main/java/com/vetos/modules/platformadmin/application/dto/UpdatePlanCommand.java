package com.vetos.modules.platformadmin.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdatePlanCommand(UUID planId, String name, BigDecimal monthlyPrice, boolean active) {}
