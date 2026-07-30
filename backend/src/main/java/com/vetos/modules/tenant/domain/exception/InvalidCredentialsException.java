package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "E-posta veya sifre hatali");
    }
}
