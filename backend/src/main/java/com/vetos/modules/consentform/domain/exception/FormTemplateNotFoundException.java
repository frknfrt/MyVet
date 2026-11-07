package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

/** Adi NotFoundException ile bittigi icin 404. */
public class FormTemplateNotFoundException extends DomainException {

    public FormTemplateNotFoundException(UUID id) {
        super("FORM_TEMPLATE_NOT_FOUND", "Form şablonu bulunamadı: " + id);
    }
}
