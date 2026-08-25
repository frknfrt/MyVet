package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.DrugInteractionWarning;

import java.util.UUID;

public record DrugInteractionWarningResponse(UUID drugAId, String drugAName, UUID drugBId, String drugBName) {
    public static DrugInteractionWarningResponse from(DrugInteractionWarning w) {
        return new DrugInteractionWarningResponse(w.drugAId(), w.drugAName(), w.drugBId(), w.drugBName());
    }
}
