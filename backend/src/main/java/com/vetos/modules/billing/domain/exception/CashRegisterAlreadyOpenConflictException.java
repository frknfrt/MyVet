package com.vetos.modules.billing.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class CashRegisterAlreadyOpenConflictException extends DomainException {
    public CashRegisterAlreadyOpenConflictException(UUID branchId) {
        super("CASH_REGISTER_ALREADY_OPEN", "Bu subede zaten acik bir kasa oturumu var: " + branchId);
    }
}
