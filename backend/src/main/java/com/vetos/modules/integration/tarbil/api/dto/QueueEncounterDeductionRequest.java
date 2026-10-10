package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QueueEncounterDeductionRequest(@NotNull UUID tarbilDiseaseId) {}
