package com.vetos.modules.encounter.domain;

import java.util.List;
import java.util.UUID;

public record EncounterClinicalContext(
    UUID encounterId, UUID patientId, String currentAssessment, List<PastEncounterSummary> recentHistory
) {}
