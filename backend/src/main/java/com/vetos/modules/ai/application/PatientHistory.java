package com.vetos.modules.ai.application;

import com.vetos.modules.encounter.domain.PastEncounterSummary;
import com.vetos.modules.encounter.domain.PrescriptionHistoryEntry;
import com.vetos.modules.encounter.domain.VaccinationHistoryEntry;
import com.vetos.modules.imaging.domain.ImagingHistoryEntry;
import com.vetos.modules.lab.domain.LabResultHistoryEntry;
import com.vetos.modules.patient.domain.PatientClinicalProfile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * AI önerisine giden hastanın son 1 yıllık geçmişi (spec 2026-10-25-ai-hasta-gecmisi).
 * profile null = hasta kartı okunamadı.
 */
public record PatientHistory(
    LocalDate encounterDate, PatientClinicalProfile profile, BigDecimal encounterWeightKg,
    Section<PastEncounterSummary> encounters, Section<PrescriptionHistoryEntry> prescriptions,
    Section<VaccinationHistoryEntry> vaccinations, Section<LabResultHistoryEntry> labs,
    Section<ImagingHistoryEntry> imaging, UUID currentEncounterId
) {
    /** truncated: tavan aşıldı, en yeniler alındı. failed: okunamadı. */
    public record Section<T>(List<T> items, boolean truncated, boolean failed) {
        public static <T> Section<T> unreadable() {
            return new Section<>(List.of(), false, true);
        }

        static <T> Section<T> capped(List<T> all, int max) {
            return all.size() > max ? new Section<>(List.copyOf(all.subList(0, max)), true, false) : new Section<>(List.copyOf(all), false, false);
        }
    }
}
