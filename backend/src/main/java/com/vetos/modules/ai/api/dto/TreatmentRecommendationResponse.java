package com.vetos.modules.ai.api.dto;

import com.vetos.modules.ai.application.TreatmentRecommendationResult;
import java.util.UUID;

public record TreatmentRecommendationResponse(UUID aiJobId, String suggestionText, boolean modelConnected) {
    public static TreatmentRecommendationResponse from(TreatmentRecommendationResult r) {
        return new TreatmentRecommendationResponse(r.aiJobId(), r.suggestionText(), r.modelConnected());
    }
}
