package com.vetos.modules.patient.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class PatientNotFoundException extends DomainException {
    public PatientNotFoundException(UUID id) {
        super("PATIENT_NOT_FOUND", "Hasta bulunamadi: " + id);
    }
}
