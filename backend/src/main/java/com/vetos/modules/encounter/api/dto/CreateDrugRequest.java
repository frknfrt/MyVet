package com.vetos.modules.encounter.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDrugRequest(@NotBlank String name, String activeIngredient, boolean isControlled) {}
