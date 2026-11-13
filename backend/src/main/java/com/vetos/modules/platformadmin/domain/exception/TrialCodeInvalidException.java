package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TrialCodeInvalidException extends DomainException {
    public TrialCodeInvalidException(int attemptsLeft) {
        super("TRIAL_CODE_INVALID", attemptsLeft > 0
            ? "Doğrulama kodu hatalı. Kalan deneme hakkı: " + attemptsLeft
            : "Doğrulama kodu hatalı ve deneme hakkınız doldu. Lütfen yeni kod isteyin.");
    }
}
