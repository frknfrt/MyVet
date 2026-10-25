package com.vetos.modules.encounter.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** AI önerisi için reçete geçmişi (spec 2026-10-25-ai-hasta-gecmisi). */
public record PrescriptionHistoryEntry(UUID encounterId, LocalDate issuedDate, List<Item> items) {
    public record Item(String drugName, String dosage, String frequency, int durationDays, DrugRoute route) {}
}
