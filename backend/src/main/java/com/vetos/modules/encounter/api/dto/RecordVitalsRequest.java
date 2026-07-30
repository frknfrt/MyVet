package com.vetos.modules.encounter.api.dto;

import java.math.BigDecimal;

public record RecordVitalsRequest(BigDecimal weightKg, BigDecimal temperatureC, Integer heartRate, Integer respiratoryRate) {}
