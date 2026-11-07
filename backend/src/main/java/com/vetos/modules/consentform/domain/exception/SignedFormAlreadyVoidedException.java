package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Varsayilan eslemeyle 422. */
public class SignedFormAlreadyVoidedException extends DomainException {

    public SignedFormAlreadyVoidedException() {
        super("SIGNED_FORM_ALREADY_VOIDED", "Bu form zaten iptal edilmiş");
    }
}
