package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class EncounterNotFoundException extends DomainException {
    public EncounterNotFoundException(UUID id) {
        super("ENCOUNTER_NOT_FOUND", "Muayene bulunamadi: " + id);
    }
}
