package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.PastEncounterSummary;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterLookupAdapterTest {

    @Mock private EncounterJpaRepository jpaRepository;
    @Mock private PrescriptionJpaRepository prescriptions;
    @Mock private PrescriptionItemJpaRepository prescriptionItems;
    @Mock private com.vetos.modules.encounter.domain.DrugCatalogRepository drugCatalog;

    private EncounterLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EncounterLookupAdapter(jpaRepository, prescriptions, prescriptionItems, drugCatalog);
    }

    private Encounter anEncounter(UUID patientId, Instant date, String assessment, String plan, boolean finalize) {
        Encounter e = Encounter.start(UUID.randomUUID(), patientId, UUID.randomUUID(), null, null);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(e, "encounterDate", date);
        e.updateSoap("s", "o", assessment, plan);
        if (finalize) {
            e.finalizeEncounter();
        }
        return e;
    }

    @Test
    void should_returnCurrentAssessmentAndRecentFinalizedHistory_excludingCurrentAndDrafts() {
        UUID patientId = UUID.randomUUID();
        Instant now = Instant.now();
        Encounter current = anEncounter(patientId, now, "Guncel degerlendirme", "Guncel plan", false);
        Encounter past1 = anEncounter(patientId, now.minus(1, ChronoUnit.DAYS), "Gecmis 1", "Plan 1", true);
        Encounter past2 = anEncounter(patientId, now.minus(2, ChronoUnit.DAYS), "Gecmis 2", "Plan 2", true);
        Encounter draftPast = anEncounter(patientId, now.minus(3, ChronoUnit.DAYS), "Taslak", "Taslak plan", false);
        when(jpaRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(jpaRepository.findByPatientId(patientId)).thenReturn(List.of(current, past1, past2, draftPast));

        EncounterClinicalContext context = adapter.findClinicalContext(current.getId(), 5);

        assertThat(context.currentAssessment()).isEqualTo("Guncel degerlendirme");
        assertThat(context.patientId()).isEqualTo(patientId);
        assertThat(context.recentHistory()).hasSize(2);
        assertThat(context.recentHistory().get(0).assessment()).isEqualTo("Gecmis 1");
        assertThat(context.recentHistory().get(1).assessment()).isEqualTo("Gecmis 2");
    }

    @Test
    void should_limitHistoryToGivenLimit() {
        UUID patientId = UUID.randomUUID();
        Instant now = Instant.now();
        Encounter current = anEncounter(patientId, now, "Guncel", "Plan", false);
        List<Encounter> all = new ArrayList<>();
        all.add(current);
        for (int i = 1; i <= 7; i++) {
            all.add(anEncounter(patientId, now.minus(i, ChronoUnit.DAYS), "Gecmis " + i, "Plan " + i, true));
        }
        when(jpaRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(jpaRepository.findByPatientId(patientId)).thenReturn(all);

        EncounterClinicalContext context = adapter.findClinicalContext(current.getId(), 3);

        assertThat(context.recentHistory()).hasSize(3);
        assertThat(context.recentHistory().get(0).assessment()).isEqualTo("Gecmis 1");
    }

    @Test
    void findClinicalContext_includesEncounterDateAndWeight() {
        UUID patientId = UUID.randomUUID();
        Instant date = Instant.parse("2026-10-20T08:00:00Z");
        Encounter current = anEncounter(patientId, date, "a", "p", false);
        ReflectionTestUtils.setField(current, "weightKg", new java.math.BigDecimal("12.4"));
        when(jpaRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(jpaRepository.findByPatientId(patientId)).thenReturn(List.of(current));

        EncounterClinicalContext context = adapter.findClinicalContext(current.getId(), 5);

        assertThat(context.encounterDate()).isEqualTo(date);
        assertThat(context.weightKg()).isEqualByComparingTo("12.4");
    }

    private com.vetos.modules.encounter.domain.Prescription prescription(java.time.LocalDate date, com.vetos.modules.encounter.domain.PrescriptionStatus status) {
        var p = org.mockito.Mockito.mock(com.vetos.modules.encounter.domain.Prescription.class);
        org.mockito.Mockito.lenient().when(p.getId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.lenient().when(p.getIssuedDate()).thenReturn(date);
        org.mockito.Mockito.lenient().when(p.getStatus()).thenReturn(status);
        return p;
    }

    private static final java.time.LocalDate FROM = java.time.LocalDate.of(2025, 10, 20);
    private static final java.time.LocalDate TO = java.time.LocalDate.of(2026, 10, 20);

    private void stubPrescriptions(UUID patientId, int limit, List<com.vetos.modules.encounter.domain.Prescription> found) {
        when(prescriptions.findByPatientIdAndStatusNotAndIssuedDateBetweenOrderByIssuedDateDesc(patientId,
            com.vetos.modules.encounter.domain.PrescriptionStatus.CANCELLED, FROM, TO, org.springframework.data.domain.PageRequest.of(0, limit)))
            .thenReturn(found);
    }

    @Test
    void findPrescriptionHistory_mapsTheBoundedQuery_readingAllItemsInOneQuery() {
        UUID patientId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        var newer = prescription(FROM.plusDays(10), com.vetos.modules.encounter.domain.PrescriptionStatus.ACTIVE);
        when(newer.getEncounterId()).thenReturn(encounterId);
        var older = prescription(FROM, com.vetos.modules.encounter.domain.PrescriptionStatus.FULFILLED);
        var item = org.mockito.Mockito.mock(com.vetos.modules.encounter.domain.PrescriptionItem.class);
        UUID prescriptionId = newer.getId();
        when(item.getPrescriptionId()).thenReturn(prescriptionId);
        when(item.getDrugName()).thenReturn("Metacam");
        when(item.getDosage()).thenReturn("1.5 mg");
        when(item.getFrequency()).thenReturn("günde 1");
        when(item.getDurationDays()).thenReturn(5);
        when(item.getRoute()).thenReturn(com.vetos.modules.encounter.domain.DrugRoute.ORAL);
        stubPrescriptions(patientId, 31, List.of(newer, older));
        when(prescriptionItems.findByPrescriptionIdIn(List.of(newer.getId(), older.getId()))).thenReturn(List.of(item));

        var history = adapter.findPrescriptionHistory(patientId, FROM, TO, 31);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).issuedDate()).isEqualTo(FROM.plusDays(10));
        assertThat(history.get(0).encounterId()).isEqualTo(encounterId);
        assertThat(history.get(0).items()).containsExactly(new com.vetos.modules.encounter.domain.PrescriptionHistoryEntry.Item(
            "Metacam", "1.5 mg", "günde 1", 5, com.vetos.modules.encounter.domain.DrugRoute.ORAL));
        assertThat(history.get(1).items()).isEmpty();
        org.mockito.Mockito.verify(prescriptionItems, org.mockito.Mockito.never()).findByPrescriptionId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void findPrescriptionHistory_usesCatalogNameForCatalogDrugs() {
        UUID patientId = UUID.randomUUID();
        var p = prescription(FROM.plusDays(1), com.vetos.modules.encounter.domain.PrescriptionStatus.ACTIVE);
        UUID drugId = UUID.randomUUID();
        var item = org.mockito.Mockito.mock(com.vetos.modules.encounter.domain.PrescriptionItem.class);
        UUID prescriptionId = p.getId();
        when(item.getPrescriptionId()).thenReturn(prescriptionId);
        when(item.getDrugId()).thenReturn(drugId);
        org.mockito.Mockito.lenient().when(item.getDrugName()).thenReturn(null);
        var drug = org.mockito.Mockito.mock(com.vetos.modules.encounter.domain.DrugCatalog.class);
        when(drug.getName()).thenReturn("Metacam");
        when(drugCatalog.findById(drugId)).thenReturn(Optional.of(drug));
        stubPrescriptions(patientId, 5, List.of(p));
        when(prescriptionItems.findByPrescriptionIdIn(List.of(p.getId()))).thenReturn(List.of(item));

        var history = adapter.findPrescriptionHistory(patientId, FROM, TO, 5);

        assertThat(history.get(0).items().get(0).drugName()).isEqualTo("Metacam");
    }

    @Test
    void findClinicalContext_excludesEncountersAfterTheCurrentOne_beforeApplyingTheLimit() {
        UUID patientId = UUID.randomUUID();
        Instant date = Instant.parse("2025-06-01T09:00:00Z");
        Encounter current = anEncounter(patientId, date, "a", "p", false);
        List<Encounter> all = new ArrayList<>();
        all.add(current);
        for (int i = 1; i <= 3; i++) all.add(anEncounter(patientId, date.plus(i * 30L, ChronoUnit.DAYS), "Sonra " + i, "p", true));
        all.add(anEncounter(patientId, date.minus(10, ChronoUnit.DAYS), "Once 1", "p", true));
        all.add(anEncounter(patientId, date.minus(20, ChronoUnit.DAYS), "Once 2", "p", true));
        when(jpaRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(jpaRepository.findByPatientId(patientId)).thenReturn(all);

        EncounterClinicalContext context = adapter.findClinicalContext(current.getId(), 2);

        assertThat(context.recentHistory()).extracting(PastEncounterSummary::assessment).containsExactly("Once 1", "Once 2");
    }

    @Test
    void should_throwEncounterNotFound_when_encounterDoesNotExist() {
        UUID encounterId = UUID.randomUUID();
        when(jpaRepository.findById(encounterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.findClinicalContext(encounterId, 5))
            .isInstanceOf(EncounterNotFoundException.class);
    }
}
