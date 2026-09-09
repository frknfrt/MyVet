package com.vetos.modules.encounter.domain;

import java.util.List;
import java.util.UUID;

public record EncounterClinicalContext(
    UUID encounterId, UUID patientId, String currentAssessment,
    String subjective, String objective, String vitalsSummary, String physicalExamSummary,
    List<PastEncounterSummary> recentHistory
) {}
