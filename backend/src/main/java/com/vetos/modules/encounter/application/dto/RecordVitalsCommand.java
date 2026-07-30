package com.vetos.modules.encounter.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordVitalsCommand(
    UUID encounterId, BigDecimal weightKg, BigDecimal temperatureC, Integer heartRate, Integer respiratoryRate
) {}
