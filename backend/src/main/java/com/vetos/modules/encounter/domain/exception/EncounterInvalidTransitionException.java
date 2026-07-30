package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;
import com.vetos.modules.encounter.domain.EncounterStatus;

public class EncounterInvalidTransitionException extends DomainException {
    public EncounterInvalidTransitionException(EncounterStatus from, EncounterStatus to) {
        super("ENCOUNTER_INVALID_TRANSITION", "Muayene " + from + " durumundan " + to + " durumuna gecemez");
    }
}
