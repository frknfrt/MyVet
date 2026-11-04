package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;

public class PasswordResetTokenInvalidException extends DomainException {
    public PasswordResetTokenInvalidException() {
        super("PASSWORD_RESET_TOKEN_INVALID",
            "Bu şifre sıfırlama bağlantısı geçersiz ya da süresi dolmuş. Yeni bir bağlantı isteyin.");
    }
}
