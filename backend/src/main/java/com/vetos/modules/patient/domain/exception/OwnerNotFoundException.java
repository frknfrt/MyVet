package com.vetos.modules.patient.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class OwnerNotFoundException extends DomainException {
    public OwnerNotFoundException(UUID id) {
        super("OWNER_NOT_FOUND", "Sahip bulunamadi: " + id);
    }
}
