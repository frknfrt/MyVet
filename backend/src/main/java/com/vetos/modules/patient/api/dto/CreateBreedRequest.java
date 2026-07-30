package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBreedRequest(@NotBlank String name) {}
