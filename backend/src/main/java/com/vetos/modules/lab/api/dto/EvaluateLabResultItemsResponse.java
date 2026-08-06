package com.vetos.modules.lab.api.dto;

import com.vetos.modules.lab.application.dto.LabResultEvaluation;

import java.util.List;

public record EvaluateLabResultItemsResponse(List<EvaluatedLabItemResponse> items, String draftSummary) {
    public static EvaluateLabResultItemsResponse from(LabResultEvaluation e) {
        return new EvaluateLabResultItemsResponse(e.items().stream().map(EvaluatedLabItemResponse::from).toList(), e.draftSummary());
    }
}
