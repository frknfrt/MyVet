package com.vetos.modules.ai.application;

import java.util.UUID;

public record TreatmentRecommendationResult(UUID aiJobId, String suggestionText, boolean modelConnected) {}
