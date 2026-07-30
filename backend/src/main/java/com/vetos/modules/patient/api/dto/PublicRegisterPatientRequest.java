package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PublicRegisterPatientRequest(
    @NotNull UUID ownerId,
    @NotNull UUID speciesId,
    @NotBlank String name
) {}
