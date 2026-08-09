package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class PlatformAdminInvalidCredentialsException extends DomainException {
    public PlatformAdminInvalidCredentialsException() {
        super("PLATFORM_ADMIN_INVALID_CREDENTIALS", "E-posta veya sifre hatali");
    }
}
