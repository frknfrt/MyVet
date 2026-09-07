package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiJobDecisionRepository;
import com.vetos.modules.ai.domain.AiJobRepository;
import com.vetos.modules.ai.domain.exception.AiJobNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordAiJobDecisionUseCase {

    private final AiJobRepository aiJobRepository;
    private final AiJobDecisionRepository aiJobDecisionRepository;

    @Transactional
    public void execute(RecordAiJobDecisionCommand command) {
        aiJobRepository.findById(command.aiJobId())
            .orElseThrow(() -> new AiJobNotFoundException(command.aiJobId()));

        AiJobDecision decision = aiJobDecisionRepository.findByAiJobId(command.aiJobId())
            .orElseGet(() -> AiJobDecision.createPending(command.aiJobId()));

        decision.decide(command.status(), command.appliedContent(), command.decidedByStaffUserId());
        aiJobDecisionRepository.save(decision);
    }
}
