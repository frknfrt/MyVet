package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;

public class EncounterCancelledConflictException extends DomainException {
    public EncounterCancelledConflictException() {
        super("ENCOUNTER_CANCELLED", "Bu muayene iptal edilmiş; değişiklik yapılamaz");
    }
}
