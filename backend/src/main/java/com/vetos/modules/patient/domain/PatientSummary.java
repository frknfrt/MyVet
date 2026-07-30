package com.vetos.modules.patient.domain;

import java.util.UUID;

public record PatientSummary(UUID id, String name, UUID ownerId, String speciesName) {}
