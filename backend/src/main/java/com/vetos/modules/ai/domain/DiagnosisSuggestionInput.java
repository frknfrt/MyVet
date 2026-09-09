package com.vetos.modules.ai.domain;

import java.util.List;

public record DiagnosisSuggestionInput(
    String subjective, String objective, String physicalExamSummary, String vitalsSummary, List<HistoryEntry> history
) {}
