package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

public class FormTemplateChangedConflictException extends DomainException {
    public FormTemplateChangedConflictException() {
        super("FORM_TEMPLATE_CHANGED", "Form şablonu değişti. Formu kapatıp yeniden açın, güncel metni okuyup yeniden imzalayın.");
    }
}
