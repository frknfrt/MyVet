package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Varsayilan eslemeyle 422. */
public class InvalidEncounterException extends DomainException {

    public InvalidEncounterException() {
        super("INVALID_ENCOUNTER", "Muayene bu hastaya ait değil");
    }
}
