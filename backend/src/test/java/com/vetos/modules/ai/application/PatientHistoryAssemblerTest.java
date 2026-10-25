package com.vetos.modules.ai.application;

import com.vetos.modules.encounter.domain.*;
import com.vetos.modules.imaging.domain.ImagingRecordLookupPort;
import com.vetos.modules.lab.domain.LabResultLookupPort;
import com.vetos.modules.patient.domain.PatientClinicalProfile;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.Sex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientHistoryAssemblerTest {

    @Mock private PatientLookupPort patients;
    @Mock private EncounterLookupPort encounters;
    @Mock private VaccinationLookupPort vaccinations;
    @Mock private LabResultLookupPort labs;
    @Mock private ImagingRecordLookupPort imaging;

    private PatientHistoryAssembler assembler;

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID ENCOUNTER = UUID.randomUUID();
    /** 2026-10-01 12:00 İstanbul. */
    private static final Instant DATE = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant SINCE = DATE.minus(365, ChronoUnit.DAYS);
    private static final LocalDate SINCE_DAY = LocalDate.of(2025, 10, 1);
    /** Muayene günü (İstanbul) ve ertesi günün başlangıcı: üst sınır. */
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    private static final Instant BEFORE = Instant.parse("2026-10-01T21:00:00Z");
    private static final PatientClinicalProfile PROFILE =
        new PatientClinicalProfile("Köpek", null, Sex.MALE, false, null, null, null, null);

    @BeforeEach
    void setUp() {
        assembler = new PatientHistoryAssembler(patients, encounters, vaccinations, labs, imaging);
    }

    private EncounterClinicalContext context(List<PastEncounterSummary> history) {
        return new EncounterClinicalContext(ENCOUNTER, PATIENT, "", "s", "o", "", "", history, DATE, new BigDecimal("12"));
    }

    private void stubAllEmpty() {
        lenient().when(patients.findClinicalProfile(PATIENT)).thenReturn(Optional.of(PROFILE));
        lenient().when(encounters.findPrescriptionHistory(PATIENT, SINCE_DAY, DAY, 31)).thenReturn(List.of());
        lenient().when(vaccinations.findAdministeredBetween(PATIENT, SINCE_DAY, DAY, 21)).thenReturn(List.of());
        lenient().when(labs.findCompletedBetween(PATIENT, SINCE, BEFORE, 16)).thenReturn(List.of());
        lenient().when(imaging.findCompletedBetween(PATIENT, SINCE, BEFORE, 11)).thenReturn(List.of());
    }

    @Test
    void assemblesWithinOneYearWindow_passingSinceToPorts() {
        stubAllEmpty();

        PatientHistory h = assembler.assemble(context(List.of()));

        verify(encounters).findPrescriptionHistory(PATIENT, SINCE_DAY, DAY, 31);
        verify(vaccinations).findAdministeredBetween(PATIENT, SINCE_DAY, DAY, 21);
        verify(labs).findCompletedBetween(PATIENT, SINCE, BEFORE, 16);
        verify(imaging).findCompletedBetween(PATIENT, SINCE, BEFORE, 11);
        assertThat(h.encounterDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(h.profile()).isEqualTo(PROFILE);
        assertThat(h.encounterWeightKg()).isEqualByComparingTo("12");
        assertThat(h.currentEncounterId()).isEqualTo(ENCOUNTER);
    }

    @Test
    void readsOnlyTheClinicalProfile_neverNameOrOwnerData() {
        stubAllEmpty();

        assembler.assemble(context(List.of()));

        verify(patients).findClinicalProfile(PATIENT);
        verifyNoMoreInteractions(patients);
    }

    @Test
    void capsEachSectionAndMarksTruncated() {
        stubAllEmpty();
        List<PrescriptionHistoryEntry> many = IntStream.range(0, 31)
            .mapToObj(i -> new PrescriptionHistoryEntry(UUID.randomUUID(), LocalDate.of(2026, 9, 1).minusDays(i), List.of()))
            .toList();
        when(encounters.findPrescriptionHistory(PATIENT, SINCE_DAY, DAY, 31)).thenReturn(many);

        PatientHistory h = assembler.assemble(context(List.of()));

        assertThat(h.prescriptions().items()).hasSize(30).first().isEqualTo(many.get(0));
        assertThat(h.prescriptions().truncated()).isTrue();
        assertThat(h.vaccinations().truncated()).isFalse();
    }

    @Test
    void dropsEncountersOutsideWindow_andCapsAtTwenty() {
        stubAllEmpty();
        List<PastEncounterSummary> history = new ArrayList<>();
        for (int i = 1; i <= 21; i++) history.add(new PastEncounterSummary(DATE.minus(i, ChronoUnit.DAYS), "a" + i, "p"));
        PatientHistory capped = assembler.assemble(context(history));
        assertThat(capped.encounters().items()).hasSize(20);
        assertThat(capped.encounters().truncated()).isTrue();

        PatientHistory windowed = assembler.assemble(context(List.of(
            new PastEncounterSummary(DATE.minus(10, ChronoUnit.DAYS), "yeni", "p"),
            new PastEncounterSummary(DATE.minus(400, ChronoUnit.DAYS), "eski", "p"))));
        assertThat(windowed.encounters().items()).extracting(PastEncounterSummary::assessment).containsExactly("yeni");
        assertThat(windowed.encounters().truncated()).isFalse();
    }

    @Test
    void failingSectionIsMarkedUnreadable_andOthersStillRender() {
        stubAllEmpty();
        when(labs.findCompletedBetween(PATIENT, SINCE, BEFORE, 16)).thenThrow(new RuntimeException("db"));
        when(vaccinations.findAdministeredBetween(PATIENT, SINCE_DAY, DAY, 21))
            .thenReturn(List.of(new VaccinationHistoryEntry("Kuduz", LocalDate.of(2026, 5, 1), null)));

        PatientHistory h = assembler.assemble(context(List.of()));

        assertThat(h.labs().failed()).isTrue();
        assertThat(h.vaccinations().items()).hasSize(1);
        assertThat(PatientHistoryFormatter.format(h)).contains("Kuduz").contains("okunamadı");
    }

    @Test
    void failingProfileBecomesNull() {
        stubAllEmpty();
        when(patients.findClinicalProfile(PATIENT)).thenThrow(new RuntimeException("db"));

        assertThat(assembler.assemble(context(List.of())).profile()).isNull();
    }
}
