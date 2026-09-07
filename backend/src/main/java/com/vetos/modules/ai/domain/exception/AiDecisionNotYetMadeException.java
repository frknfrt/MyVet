package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiDecisionNotYetMadeException extends DomainException {
    public AiDecisionNotYetMadeException(UUID aiJobId) {
        super("AI_DECISION_NOT_YET_MADE", "Bu AI onerisi icin henuz hekim karari verilmemis: " + aiJobId);
    }
}
