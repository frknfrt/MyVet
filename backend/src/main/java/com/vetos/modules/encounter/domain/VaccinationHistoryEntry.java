package com.vetos.modules.encounter.domain;

import java.time.LocalDate;

/** AI önerisi için yapılmış aşı (spec 2026-10-25-ai-hasta-gecmisi). */
public record VaccinationHistoryEntry(String vaccineName, LocalDate administeredDate, LocalDate nextDueDate) {}
