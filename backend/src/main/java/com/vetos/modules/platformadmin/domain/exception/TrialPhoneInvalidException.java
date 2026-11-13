package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TrialPhoneInvalidException extends DomainException {
    public TrialPhoneInvalidException() {
        super("TRIAL_PHONE_INVALID", "Geçerli bir cep telefonu numarası girin (05XX XXX XX XX).");
    }
}
