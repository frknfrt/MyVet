package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Varsayilan eslemeyle 422. */
public class FormTemplateArchivedException extends DomainException {

    public FormTemplateArchivedException() {
        this("Arşivlenmiş şablon düzenlenemez");
    }

    public FormTemplateArchivedException(String message) {
        super("FORM_TEMPLATE_ARCHIVED", message);
    }
}
