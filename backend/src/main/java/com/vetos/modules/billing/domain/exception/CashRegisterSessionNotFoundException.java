package com.vetos.modules.billing.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class CashRegisterSessionNotFoundException extends DomainException {
    public CashRegisterSessionNotFoundException(UUID id) {
        super("CASH_REGISTER_SESSION_NOT_FOUND", "Kasa oturumu bulunamadi: " + id);
    }
}
