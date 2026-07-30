package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.EncounterStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EncounterDetail(
    UUID id,
    UUID patientId,
    String patientName,
    UUID staffUserId,
    String staffName,
    UUID appointmentId,
    Instant encounterDate,
    String subjective,
    String objective,
    String assessment,
    String plan,
    BigDecimal weightKg,
    BigDecimal temperatureC,
    Integer heartRate,
    Integer respiratoryRate,
    String templateUsed,
    EncounterStatus status,
    boolean aiGenerated,
    Instant finalizedAt
) {}
