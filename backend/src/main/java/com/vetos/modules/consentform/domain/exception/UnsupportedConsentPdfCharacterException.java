package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Karakter kaybeden bir PDF üretmek yerine açıklayıcı 422. */
public class UnsupportedConsentPdfCharacterException extends DomainException {
    public UnsupportedConsentPdfCharacterException(int codePoint) {
        super("UNSUPPORTED_CONSENT_PDF_CHARACTER", "PDF yazı tipinin desteklemediği karakter: U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT));
    }
}
