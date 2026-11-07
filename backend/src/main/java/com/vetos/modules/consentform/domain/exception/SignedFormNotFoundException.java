package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

/** Adi NotFoundException ile bittigi icin 404. */
public class SignedFormNotFoundException extends DomainException {

    public SignedFormNotFoundException(UUID id) {
        super("SIGNED_FORM_NOT_FOUND", "İmzalı form bulunamadı: " + id);
    }
}
