package com.vetos.modules.encounter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateDrugRequest(
    @NotBlank String name,
    String activeIngredient,
    boolean isControlled,
    @NotNull List<UUID> interactingDrugIds
) {}
