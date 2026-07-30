package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.DrugSummary;

import java.util.UUID;

public record DrugResponse(UUID id, String name, String activeIngredient, boolean isControlled) {
    public static DrugResponse from(DrugSummary s) {
        return new DrugResponse(s.id(), s.name(), s.activeIngredient(), s.isControlled());
    }
}
