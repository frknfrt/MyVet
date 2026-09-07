package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiDecisionMissingAppliedContentException extends DomainException {
    public AiDecisionMissingAppliedContentException(UUID aiJobId) {
        super("AI_DECISION_MISSING_APPLIED_CONTENT", "ACCEPTED_WITH_EDITS karari icin uygulanan icerik (appliedContent) zorunludur: " + aiJobId);
    }
}
