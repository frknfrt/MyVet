package com.vetos.modules.ai.api.dto;

import com.vetos.modules.ai.domain.DecisionStatus;
import jakarta.validation.constraints.NotNull;

public record DecideTreatmentRecommendationRequest(@NotNull DecisionStatus status, String appliedContent) {}
