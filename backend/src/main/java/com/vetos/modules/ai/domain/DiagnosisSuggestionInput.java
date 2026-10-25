package com.vetos.modules.ai.domain;

/** patientHistory: PatientHistoryFormatter çıktısı (son 1 yıl; spec 2026-10-25). */
public record DiagnosisSuggestionInput(
    String subjective, String objective, String physicalExamSummary, String vitalsSummary, String patientHistory
) {}
