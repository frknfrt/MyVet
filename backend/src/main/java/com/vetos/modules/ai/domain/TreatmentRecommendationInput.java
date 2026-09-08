package com.vetos.modules.ai.domain;

import java.util.List;

public record TreatmentRecommendationInput(String currentAssessment, List<HistoryEntry> history) {}
