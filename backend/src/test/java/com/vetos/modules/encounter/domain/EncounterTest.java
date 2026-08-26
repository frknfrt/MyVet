package com.vetos.modules.encounter.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EncounterTest {

    @Test
    void should_storeFindings_when_updatePhysicalExamCalledWithFindings() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), null, null);
        List<PhysicalExamFinding> findings = List.of(
            new PhysicalExamFinding(ExamBodySystem.CARDIOVASCULAR, ExamFindingStatus.ABNORMAL, "Üfürüm duyuldu"),
            new PhysicalExamFinding(ExamBodySystem.RESPIRATORY, ExamFindingStatus.NORMAL, null)
        );

        encounter.updatePhysicalExam(findings);

        assertThat(encounter.getPhysicalExamFindings()).isEqualTo(findings);
    }

    @Test
    void should_startWithEmptyFindings_when_encounterStarted() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), null, null);

        assertThat(encounter.getPhysicalExamFindings()).isEmpty();
    }
}
