package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class PlanNotFoundException extends DomainException {
    public PlanNotFoundException(UUID id) {
        super("PLAN_NOT_FOUND", "Plan bulunamadi: " + id);
    }
}
