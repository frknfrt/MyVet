package com.vetos.modules.ai.domain;

import java.util.Optional;
import java.util.UUID;

public interface AiJobDecisionRepository {
    AiJobDecision save(AiJobDecision decision);
    Optional<AiJobDecision> findByAiJobId(UUID aiJobId);
}
