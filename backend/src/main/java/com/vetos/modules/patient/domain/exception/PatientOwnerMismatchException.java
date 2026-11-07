package com.vetos.modules.patient.domain.exception;

import com.vetos.platform.exception.DomainException;

public class PatientOwnerMismatchException extends DomainException {
    public PatientOwnerMismatchException() {
        super("PATIENT_OWNER_MISMATCH", "Hasta bu sahibe ait degil");
    }
}
