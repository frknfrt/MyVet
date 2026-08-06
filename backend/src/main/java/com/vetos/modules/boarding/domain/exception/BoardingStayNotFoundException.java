package com.vetos.modules.boarding.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class BoardingStayNotFoundException extends DomainException {
    public BoardingStayNotFoundException(UUID id) {
        super("BOARDING_STAY_NOT_FOUND", "Konaklama kaydi bulunamadi: " + id);
    }
}
