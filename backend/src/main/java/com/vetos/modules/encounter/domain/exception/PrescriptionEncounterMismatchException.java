package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;

public class PrescriptionEncounterMismatchException extends DomainException {
    public PrescriptionEncounterMismatchException() {
        super("PRESCRIPTION_ENCOUNTER_MISMATCH", "Muayene bu hastaya ait değil");
    }
}
