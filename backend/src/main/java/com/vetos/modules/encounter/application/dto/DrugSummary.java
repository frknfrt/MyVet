package com.vetos.modules.encounter.application.dto;

import java.util.List;
import java.util.UUID;

public record DrugSummary(
    UUID id, String name, String activeIngredient, boolean isControlled, List<UUID> interactingDrugIds
) {}
