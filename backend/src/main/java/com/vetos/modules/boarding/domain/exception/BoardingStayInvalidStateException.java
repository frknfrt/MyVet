package com.vetos.modules.boarding.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class BoardingStayInvalidStateException extends DomainException {
    public BoardingStayInvalidStateException(UUID id, String reason) {
        super("BOARDING_STAY_INVALID_STATE", reason);
    }
}
