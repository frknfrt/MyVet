package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Sinif adi "ServiceUnavailableException" ile bittigi icin GlobalExceptionHandler 503 doner. */
public class AiBusyServiceUnavailableException extends DomainException {

    public AiBusyServiceUnavailableException() {
        super("AI_BUSY", "AI şu an yoğun, lütfen birkaç saniye sonra tekrar deneyin.");
    }
}
