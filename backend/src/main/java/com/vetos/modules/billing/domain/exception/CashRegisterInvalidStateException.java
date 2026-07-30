package com.vetos.modules.billing.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class CashRegisterInvalidStateException extends DomainException {
    public CashRegisterInvalidStateException(UUID sessionId, String reason) {
        super("CASH_REGISTER_INVALID_STATE", "Kasa islemi gecersiz (" + sessionId + "): " + reason);
    }
}
