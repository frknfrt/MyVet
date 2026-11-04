package com.vetos.modules.encounter.domain;

import com.vetos.modules.encounter.domain.exception.EncounterCancelledConflictException;
import com.vetos.modules.encounter.domain.exception.EncounterInvalidTransitionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncounterTest {

    @Test
    void should_storeFindings_when_updatePhysicalExamCalledWithFindings() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        List<PhysicalExamFinding> findings = List.of(
            new PhysicalExamFinding(ExamBodySystem.CARDIOVASCULAR, ExamFindingStatus.ABNORMAL, "Üfürüm duyuldu"),
            new PhysicalExamFinding(ExamBodySystem.RESPIRATORY, ExamFindingStatus.NORMAL, null)
        );

        encounter.updatePhysicalExam(findings);

        assertThat(encounter.getPhysicalExamFindings()).isEqualTo(findings);
    }

    @Test
    void should_becomeCancelled_when_draftCancelled() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);

        encounter.cancel();

        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.CANCELLED);
    }

    @Test
    void should_rejectCancel_when_encounterFinalized() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        encounter.finalizeEncounter();

        assertThatThrownBy(encounter::cancel).isInstanceOf(EncounterInvalidTransitionException.class);
        assertThat(encounter.getStatus()).isEqualTo(EncounterStatus.FINALIZED);
    }

    @Test
    void should_rejectEdits_when_encounterCancelled() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        encounter.cancel();

        assertThatThrownBy(() -> encounter.updateSoap("s", "o", "a", "p"))
            .isInstanceOf(EncounterCancelledConflictException.class);
        assertThatThrownBy(() -> encounter.recordVitals(null, null, 80, null))
            .isInstanceOf(EncounterCancelledConflictException.class);
        assertThatThrownBy(() -> encounter.updatePhysicalExam(List.of()))
            .isInstanceOf(EncounterCancelledConflictException.class);
        assertThatThrownBy(encounter::finalizeEncounter).isInstanceOf(EncounterInvalidTransitionException.class);
    }

    @Test
    void should_startWithEmptyFindings_when_encounterStarted() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);

        assertThat(encounter.getPhysicalExamFindings()).isEmpty();
    }
}
