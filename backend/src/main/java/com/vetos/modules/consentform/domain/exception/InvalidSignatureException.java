package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Varsayilan eslemeyle 422. */
public class InvalidSignatureException extends DomainException {

    public InvalidSignatureException(String message) {
        super("INVALID_SIGNATURE", message);
    }
}
