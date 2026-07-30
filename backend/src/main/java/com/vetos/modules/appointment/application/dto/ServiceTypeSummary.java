package com.vetos.modules.appointment.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceTypeSummary(UUID id, String name, int defaultDurationMin, BigDecimal defaultPrice) {}
