package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Odeme saglayicisi devre kesicisi acik: GlobalExceptionHandler 503 doner. */
public class PaymentProviderServiceUnavailableException extends DomainException {
    public PaymentProviderServiceUnavailableException() {
        super("PAYMENT_PROVIDER_UNAVAILABLE", "Ödeme sistemine şu an ulaşılamıyor, lütfen birkaç dakika sonra tekrar deneyin.");
    }
}
