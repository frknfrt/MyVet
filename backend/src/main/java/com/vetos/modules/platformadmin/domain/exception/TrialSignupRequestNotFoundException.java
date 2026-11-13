package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TrialSignupRequestNotFoundException extends DomainException {
    public TrialSignupRequestNotFoundException() {
        super("TRIAL_SIGNUP_REQUEST_NOT_FOUND", "Kayıt isteği bulunamadı. Lütfen formu yeniden doldurun.");
    }
}
