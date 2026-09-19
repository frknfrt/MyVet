package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AiDecisionNotYetMadeException;
import com.vetos.modules.ai.domain.exception.AiJobDecisionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordAiJobFeedbackUseCaseTest {

    @Mock private AiJobDecisionRepository aiJobDecisionRepository;

    private RecordAiJobFeedbackUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordAiJobFeedbackUseCase(aiJobDecisionRepository);
    }

    @Test
    void should_saveFeedback_when_decisionAlreadyMade() {
        UUID aiJobId = UUID.randomUUID();
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID(), aiJobId);
        decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.of(decision));

        useCase.execute(new RecordAiJobFeedbackCommand(aiJobId, AccuracyFeedback.ACCURATE));

        assertThat(decision.getAccuracyFeedback()).isEqualTo(AccuracyFeedback.ACCURATE);
        verify(aiJobDecisionRepository).save(decision);
    }

    @Test
    void should_throwAiJobDecisionNotFound_when_noDecisionRecordExists() {
        UUID aiJobId = UUID.randomUUID();
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RecordAiJobFeedbackCommand(aiJobId, AccuracyFeedback.ACCURATE)))
            .isInstanceOf(AiJobDecisionNotFoundException.class);
    }

    @Test
    void should_throwNotYetMade_when_decisionIsPending() {
        UUID aiJobId = UUID.randomUUID();
        AiJobDecision pending = AiJobDecision.createPending(UUID.randomUUID(), aiJobId);
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.execute(new RecordAiJobFeedbackCommand(aiJobId, AccuracyFeedback.ACCURATE)))
            .isInstanceOf(AiDecisionNotYetMadeException.class);
    }
}
