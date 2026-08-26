package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.UpdatePhysicalExamCommand;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.ExamBodySystem;
import com.vetos.modules.encounter.domain.ExamFindingStatus;
import com.vetos.modules.encounter.domain.PhysicalExamFinding;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdatePhysicalExamUseCaseTest {

    @Mock
    private EncounterRepository encounterRepository;

    @Test
    void should_saveEncounterWithFindings_when_encounterExists() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), null, null);
        UUID encounterId = encounter.getId();
        List<PhysicalExamFinding> findings = List.of(
            new PhysicalExamFinding(ExamBodySystem.SKIN_COAT, ExamFindingStatus.NORMAL, null)
        );
        when(encounterRepository.findById(encounterId)).thenReturn(Optional.of(encounter));

        new UpdatePhysicalExamUseCase(encounterRepository).execute(new UpdatePhysicalExamCommand(encounterId, findings));

        assertThat(encounter.getPhysicalExamFindings()).isEqualTo(findings);
        verify(encounterRepository).save(encounter);
    }

    @Test
    void should_throwEncounterNotFoundException_when_encounterIdDoesNotExist() {
        UUID encounterId = UUID.randomUUID();
        when(encounterRepository.findById(encounterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            new UpdatePhysicalExamUseCase(encounterRepository).execute(new UpdatePhysicalExamCommand(encounterId, List.of()))
        ).isInstanceOf(EncounterNotFoundException.class);
    }
}
