package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiDecisionAlreadyRecordedConflictException extends DomainException {
    public AiDecisionAlreadyRecordedConflictException(UUID aiJobId) {
        super("AI_DECISION_ALREADY_RECORDED", "Bu AI onerisi icin karar/geri bildirim zaten kaydedilmis: " + aiJobId);
    }
}
