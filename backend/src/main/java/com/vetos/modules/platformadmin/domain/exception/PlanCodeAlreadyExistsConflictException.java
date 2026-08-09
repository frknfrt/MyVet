package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class PlanCodeAlreadyExistsConflictException extends DomainException {
    public PlanCodeAlreadyExistsConflictException(String code) {
        super("PLAN_CODE_ALREADY_EXISTS", "Bu plan kodu zaten kullaniliyor: " + code);
    }
}
