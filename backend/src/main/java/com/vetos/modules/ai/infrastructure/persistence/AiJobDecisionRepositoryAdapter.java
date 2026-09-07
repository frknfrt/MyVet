package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiJobDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AiJobDecisionRepositoryAdapter implements AiJobDecisionRepository {

    private final AiJobDecisionJpaRepository jpaRepository;

    @Override
    public AiJobDecision save(AiJobDecision decision) {
        return jpaRepository.save(decision);
    }

    @Override
    public Optional<AiJobDecision> findByAiJobId(UUID aiJobId) {
        return jpaRepository.findByAiJobId(aiJobId);
    }
}
