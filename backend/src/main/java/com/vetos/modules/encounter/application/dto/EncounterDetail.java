package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.EncounterStatus;
import com.vetos.modules.encounter.domain.PhysicalExamFinding;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    List<PhysicalExamFinding> physicalExamFindings,
    EncounterStatus status,
    boolean aiGenerated,
    Instant finalizedAt
) {}
