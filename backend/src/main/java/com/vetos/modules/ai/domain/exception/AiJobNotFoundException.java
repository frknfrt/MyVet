package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiJobNotFoundException extends DomainException {
    public AiJobNotFoundException(UUID id) {
        super("AI_JOB_NOT_FOUND", "AI is kaydi bulunamadi: " + id);
    }
}
