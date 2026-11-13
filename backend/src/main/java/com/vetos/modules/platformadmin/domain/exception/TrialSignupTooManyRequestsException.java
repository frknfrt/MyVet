package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

/** HTTP 429 -- GlobalExceptionHandler son ek kurali (*TooManyRequestsException). */
public class TrialSignupTooManyRequestsException extends DomainException {
    public TrialSignupTooManyRequestsException() {
        super("TRIAL_RATE_LIMITED", "Çok fazla deneme yapıldı. Lütfen biraz sonra tekrar deneyin.");
    }
}
