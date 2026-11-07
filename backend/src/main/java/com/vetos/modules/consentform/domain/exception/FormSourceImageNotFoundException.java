package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

/** Adi NotFoundException ile bittigi icin 404. */
public class FormSourceImageNotFoundException extends DomainException {

    public FormSourceImageNotFoundException(UUID id) {
        super("FORM_SOURCE_IMAGE_NOT_FOUND", "Form kaynak görseli bulunamadı: " + id);
    }
}
