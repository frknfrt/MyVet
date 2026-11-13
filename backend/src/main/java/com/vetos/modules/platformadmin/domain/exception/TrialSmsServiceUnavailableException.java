package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

/** HTTP 503: SMS saglayicisi tanimli degil, dogrulama kodu gonderilemez. */
public class TrialSmsServiceUnavailableException extends DomainException {
    public TrialSmsServiceUnavailableException() {
        super("TRIAL_SMS_UNAVAILABLE", "Şu anda doğrulama kodu gönderemiyoruz. Lütfen daha sonra tekrar deneyin.");
    }
}
