package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class PrescriptionNotFoundException extends DomainException {
    public PrescriptionNotFoundException(UUID id) {
        super("PRESCRIPTION_NOT_FOUND", "Recete bulunamadi: " + id);
    }
}
