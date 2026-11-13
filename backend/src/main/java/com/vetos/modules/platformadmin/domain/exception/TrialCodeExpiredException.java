package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TrialCodeExpiredException extends DomainException {
    public TrialCodeExpiredException() {
        super("TRIAL_CODE_EXPIRED", "Kodun süresi doldu. Yeni kod isteyin.");
    }
}
