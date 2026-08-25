package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.DrugInteractionWarning;

import java.util.List;

public record CheckDrugInteractionsResponse(List<DrugInteractionWarningResponse> warnings) {
    public static CheckDrugInteractionsResponse from(List<DrugInteractionWarning> warnings) {
        return new CheckDrugInteractionsResponse(warnings.stream().map(DrugInteractionWarningResponse::from).toList());
    }
}
