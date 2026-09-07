package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AiDecisionAlreadyRecordedConflictException;
import com.vetos.modules.ai.domain.exception.AiJobNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordAiJobDecisionUseCaseTest {

    @Mock private AiJobRepository aiJobRepository;
    @Mock private AiJobDecisionRepository aiJobDecisionRepository;

    private RecordAiJobDecisionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordAiJobDecisionUseCase(aiJobRepository, aiJobDecisionRepository);
    }

    private AiJob anAiJob() {
        return AiJob.create(
            UUID.randomUUID(), AiTaskType.TREATMENT_RECOMMENDATION, UUID.randomUUID(),
            "Sivi tedavisi onerilir", "ollama", "llama3.1:8b", UUID.randomUUID()
        );
    }

    @Test
    void should_createAndSaveDecision_when_noDecisionExistsYet() {
        UUID aiJobId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.of(anAiJob()));
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.empty());
        when(aiJobDecisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RecordAiJobDecisionCommand(aiJobId, DecisionStatus.ACCEPTED_AS_IS, null, staffUserId));

        verify(aiJobDecisionRepository).save(argThat(d ->
            d.getDecisionStatus() == DecisionStatus.ACCEPTED_AS_IS && d.getDecidedByStaffUserId().equals(staffUserId)
        ));
    }

    @Test
    void should_throwAiJobNotFound_when_aiJobDoesNotExist() {
        UUID aiJobId = UUID.randomUUID();
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
            new RecordAiJobDecisionCommand(aiJobId, DecisionStatus.REJECTED, null, UUID.randomUUID())
        )).isInstanceOf(AiJobNotFoundException.class);
    }

    @Test
    void should_throwAlreadyRecorded_when_decisionAlreadyMade() {
        UUID aiJobId = UUID.randomUUID();
        AiJobDecision existing = AiJobDecision.createPending(aiJobId);
        existing.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.of(anAiJob()));
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(
            new RecordAiJobDecisionCommand(aiJobId, DecisionStatus.REJECTED, null, UUID.randomUUID())
        )).isInstanceOf(AiDecisionAlreadyRecordedConflictException.class);
    }
}
