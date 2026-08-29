package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class PaymentGatewayException extends DomainException {
    public PaymentGatewayException(String message, Throwable cause) {
        super("PAYMENT_GATEWAY_ERROR", "Odeme baslatilamadi, lutfen tekrar deneyin");
        initCause(cause);
    }
}
