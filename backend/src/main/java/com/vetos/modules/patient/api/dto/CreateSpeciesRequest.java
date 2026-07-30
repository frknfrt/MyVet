package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSpeciesRequest(@NotBlank String name) {}
