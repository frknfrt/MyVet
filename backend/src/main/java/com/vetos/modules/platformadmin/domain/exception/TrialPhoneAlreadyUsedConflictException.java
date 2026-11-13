package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TrialPhoneAlreadyUsedConflictException extends DomainException {
    public TrialPhoneAlreadyUsedConflictException() {
        super("TRIAL_PHONE_ALREADY_USED", "Bu telefon numarasıyla daha önce deneme başlatılmış.");
    }
}
