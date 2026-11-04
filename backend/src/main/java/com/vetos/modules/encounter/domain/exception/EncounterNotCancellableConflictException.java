package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;

public class EncounterNotCancellableConflictException extends DomainException {
    public EncounterNotCancellableConflictException(String reason) {
        super("ENCOUNTER_NOT_CANCELLABLE", "Muayene iptal edilemez: " + reason);
    }
}
