package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiDecisionAlreadyRecordedException extends DomainException {
    public AiDecisionAlreadyRecordedException(UUID aiJobId) {
        super("AI_DECISION_ALREADY_RECORDED", "Bu AI onerisi icin karar/geri bildirim zaten kaydedilmis: " + aiJobId);
    }
}
