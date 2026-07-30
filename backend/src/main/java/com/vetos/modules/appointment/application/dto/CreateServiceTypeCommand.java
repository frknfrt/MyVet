package com.vetos.modules.appointment.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateServiceTypeCommand(UUID tenantId, String name, int defaultDurationMin, BigDecimal defaultPrice) {}
