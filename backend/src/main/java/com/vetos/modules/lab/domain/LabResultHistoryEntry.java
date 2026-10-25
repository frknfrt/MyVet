package com.vetos.modules.lab.domain;

import java.time.Instant;
import java.util.List;

/** AI önerisi için tamamlanmış tahlil (spec 2026-10-25-ai-hasta-gecmisi). date = sonuç tarihi, yoksa istek tarihi. */
public record LabResultHistoryEntry(String testName, Instant date, String resultSummary, List<Value> values) {
    public record Value(String parameterName, String value, String unit, String referenceRange, LabValueFlag flag) {}
}
