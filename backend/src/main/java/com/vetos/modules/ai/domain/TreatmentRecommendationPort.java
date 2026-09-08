package com.vetos.modules.ai.domain;

public interface TreatmentRecommendationPort {
    TreatmentRecommendationDraft generate(TreatmentRecommendationInput input);
}
