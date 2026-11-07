package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Varsayilan eslemeyle 422. */
public class InvalidFormTemplateException extends DomainException {

    public InvalidFormTemplateException(String message) {
        super("INVALID_FORM_TEMPLATE", message);
    }

    public InvalidFormTemplateException(String message, Throwable cause) {
        super("INVALID_FORM_TEMPLATE", message);
        initCause(cause);
    }
}
