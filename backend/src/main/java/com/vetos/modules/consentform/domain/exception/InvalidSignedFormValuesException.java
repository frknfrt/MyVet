package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Varsayilan eslemeyle 422. */
public class InvalidSignedFormValuesException extends DomainException {

    public InvalidSignedFormValuesException(String message) {
        super("INVALID_SIGNED_FORM_VALUES", message);
    }
}
