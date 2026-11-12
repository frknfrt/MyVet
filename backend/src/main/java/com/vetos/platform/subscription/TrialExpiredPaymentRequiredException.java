package com.vetos.platform.subscription;

import com.vetos.platform.exception.DomainException;

/** HTTP 402 -- GlobalExceptionHandler son ek kurali (*PaymentRequiredException). */
public class TrialExpiredPaymentRequiredException extends DomainException {

    public static final String MESSAGE = "Deneme kullanım süreniz dolmuştur, lütfen üyelik başlatınız.";

    public TrialExpiredPaymentRequiredException() {
        super("TRIAL_EXPIRED", MESSAGE);
    }
}
