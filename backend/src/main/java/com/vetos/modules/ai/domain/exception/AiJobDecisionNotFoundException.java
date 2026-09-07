package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiJobDecisionNotFoundException extends DomainException {
    public AiJobDecisionNotFoundException(UUID aiJobId) {
        super("AI_JOB_DECISION_NOT_FOUND", "AI is karari bulunamadi (aiJobId): " + aiJobId);
    }
}
