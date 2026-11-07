package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Varsayilan eslemeyle 422. */
public class UnsupportedFormSourceImageException extends DomainException {

    public UnsupportedFormSourceImageException() {
        super("UNSUPPORTED_FORM_SOURCE_IMAGE", "Kaynak görsel JPEG, PNG veya WEBP olmalı ve en fazla 10 MB olabilir");
    }
}
