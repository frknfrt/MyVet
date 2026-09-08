package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
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

    private EncounterLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EncounterLookupAdapter(jpaRepository);
    }

    private Encounter anEncounter(UUID patientId, Instant date, String assessment, String plan, boolean finalize) {
        Encounter e = Encounter.start(patientId, UUID.randomUUID(), null, null);
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
    void should_throwEncounterNotFound_when_encounterDoesNotExist() {
        UUID encounterId = UUID.randomUUID();
        when(jpaRepository.findById(encounterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.findClinicalContext(encounterId, 5))
            .isInstanceOf(EncounterNotFoundException.class);
    }
}
