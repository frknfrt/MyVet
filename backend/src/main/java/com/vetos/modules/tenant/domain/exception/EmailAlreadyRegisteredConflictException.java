package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;

public class EmailAlreadyRegisteredConflictException extends DomainException {
    public EmailAlreadyRegisteredConflictException(String email) {
        super("EMAIL_ALREADY_REGISTERED", "Bu e-posta zaten kayitli: " + email);
    }
}
