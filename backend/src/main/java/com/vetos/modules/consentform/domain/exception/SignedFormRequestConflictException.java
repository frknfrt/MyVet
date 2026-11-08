package com.vetos.modules.consentform.domain.exception;

import com.vetos.platform.exception.DomainException;

public class SignedFormRequestConflictException extends DomainException {
    public SignedFormRequestConflictException() {
        super("SIGNED_FORM_REQUEST_CONFLICT", "Bu gönderim farklı içerikle zaten kaydedilmiş. Formlar listesini kontrol edin; yeni form için ekranı yeniden açın.");
    }
}
