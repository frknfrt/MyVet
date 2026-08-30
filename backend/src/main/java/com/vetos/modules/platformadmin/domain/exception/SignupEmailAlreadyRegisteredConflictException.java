package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class SignupEmailAlreadyRegisteredConflictException extends DomainException {
    public SignupEmailAlreadyRegisteredConflictException(String email) {
        super("SIGNUP_EMAIL_ALREADY_REGISTERED", "Bu e-posta adresi zaten kayitli: " + email);
    }
}
