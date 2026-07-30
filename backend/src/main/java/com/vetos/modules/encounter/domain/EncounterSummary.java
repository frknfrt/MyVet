package com.vetos.modules.encounter.domain;

import java.util.UUID;

public record EncounterSummary(UUID id, UUID patientId, UUID staffUserId, EncounterStatus status) {}
