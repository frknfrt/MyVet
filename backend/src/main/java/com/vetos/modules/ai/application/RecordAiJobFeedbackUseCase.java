package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiJobDecisionRepository;
import com.vetos.modules.ai.domain.exception.AiJobDecisionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordAiJobFeedbackUseCase {

    private final AiJobDecisionRepository aiJobDecisionRepository;

    @Transactional
    public void execute(RecordAiJobFeedbackCommand command) {
        AiJobDecision decision = aiJobDecisionRepository.findByAiJobId(command.aiJobId())
            .orElseThrow(() -> new AiJobDecisionNotFoundException(command.aiJobId()));

        decision.recordFeedback(command.feedback());
        aiJobDecisionRepository.save(decision);
    }
}
