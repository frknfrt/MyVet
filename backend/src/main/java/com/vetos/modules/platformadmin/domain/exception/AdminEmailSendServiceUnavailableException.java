package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

/** SMTP gonderimi basarisiz (baglanti/kimlik dogrulama hatasi) -- GlobalExceptionHandler 503 doner. */
public class AdminEmailSendServiceUnavailableException extends DomainException {
    public AdminEmailSendServiceUnavailableException(Throwable cause) {
        super("ADMIN_EMAIL_SEND_FAILED", "E-posta gonderilemedi, lutfen tekrar deneyin");
        initCause(cause);
    }
}
