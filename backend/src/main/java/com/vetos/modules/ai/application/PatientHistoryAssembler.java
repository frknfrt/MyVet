package com.vetos.modules.ai.application;

import com.vetos.modules.ai.application.PatientHistory.Section;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.imaging.domain.ImagingRecordLookupPort;
import com.vetos.modules.lab.domain.LabResultLookupPort;
import com.vetos.modules.patient.domain.PatientClinicalProfile;
import com.vetos.modules.patient.domain.PatientLookupPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

/**
 * Hastanın son 1 yıllık geçmişini portlardan toplar. Portlar "sınır + 1" kayıt döner; fazlası varsa bölüm kısaltılmış sayılır.
 * Spec: 2026-10-25-ai-hasta-gecmisi.
 * Bir bölüm okunamazsa o bölüm "okunamadı" olur; öneri yine üretilir.
 */
@Service
@Slf4j
public class PatientHistoryAssembler {

    static final int WINDOW_DAYS = 365;
    static final int MAX_ENCOUNTERS = 20;
    static final int MAX_PRESCRIPTIONS = 30;
    static final int MAX_VACCINATIONS = 20;
    static final int MAX_LABS = 15;
    static final int MAX_IMAGING = 10;
    static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final PatientLookupPort patients;
    private final EncounterLookupPort encounters;
    private final VaccinationLookupPort vaccinations;
    private final LabResultLookupPort labs;
    private final ImagingRecordLookupPort imaging;

    public PatientHistoryAssembler(PatientLookupPort patients, EncounterLookupPort encounters, VaccinationLookupPort vaccinations,
                                   LabResultLookupPort labs, ImagingRecordLookupPort imaging) {
        this.patients = patients;
        this.encounters = encounters;
        this.vaccinations = vaccinations;
        this.labs = labs;
        this.imaging = imaging;
    }

    public PatientHistory assemble(EncounterClinicalContext context) {
        Instant date = context.encounterDate() != null ? context.encounterDate() : Instant.now();
        Instant since = date.minus(WINDOW_DAYS, ChronoUnit.DAYS);
        LocalDate sinceDay = since.atZone(ISTANBUL).toLocalDate();
        // Üst sınır: muayenenin günü dahil, sonrası hariç (lab/görüntülemede o güne kadar istenmiş olanlar; 2026-10-25).
        LocalDate encounterDay = date.atZone(ISTANBUL).toLocalDate();
        Instant requestedBefore = encounterDay.plusDays(1).atStartOfDay(ISTANBUL).toInstant();
        var patientId = context.patientId();

        PatientClinicalProfile profile;
        try {
            profile = patients.findClinicalProfile(patientId).orElse(null);
        } catch (RuntimeException e) {
            log.warn("AI hasta geçmişi: hasta kartı okunamadı: {}", e.getMessage());
            profile = null;
        }

        return new PatientHistory(
            encounterDay, profile, context.weightKg(),
            section("muayene", MAX_ENCOUNTERS, () -> context.recentHistory().stream()
                .filter(e -> e.date() != null && !e.date().isBefore(since)).toList()),
            section("reçete", MAX_PRESCRIPTIONS, () -> encounters.findPrescriptionHistory(patientId, sinceDay, encounterDay, MAX_PRESCRIPTIONS + 1)),
            section("aşı", MAX_VACCINATIONS, () -> vaccinations.findAdministeredBetween(patientId, sinceDay, encounterDay, MAX_VACCINATIONS + 1)),
            section("lab", MAX_LABS, () -> labs.findCompletedBetween(patientId, since, requestedBefore, MAX_LABS + 1)),
            section("görüntüleme", MAX_IMAGING, () -> imaging.findCompletedBetween(patientId, since, requestedBefore, MAX_IMAGING + 1)),
            context.encounterId()
        );
    }

    private <T> Section<T> section(String name, int max, Supplier<List<T>> read) {
        try {
            return Section.capped(read.get(), max);
        } catch (RuntimeException e) {
            log.warn("AI hasta geçmişi: {} bölümü okunamadı: {}", name, e.getMessage());
            return Section.unreadable();
        }
    }
}
