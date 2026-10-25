package com.vetos.modules.encounter.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** encounterDate/weightKg: AI hasta geçmişi penceresi ve kilo önceliği için (spec 2026-10-25). */
public record EncounterClinicalContext(
    UUID encounterId, UUID patientId, String currentAssessment,
    String subjective, String objective, String vitalsSummary, String physicalExamSummary,
    List<PastEncounterSummary> recentHistory, Instant encounterDate, BigDecimal weightKg
) {}
