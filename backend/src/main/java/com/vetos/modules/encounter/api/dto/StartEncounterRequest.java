package com.vetos.modules.encounter.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartEncounterRequest(@NotNull UUID patientId, UUID appointmentId, String templateUsed) {}
