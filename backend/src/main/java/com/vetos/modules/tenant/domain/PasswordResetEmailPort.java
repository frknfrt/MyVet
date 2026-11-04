package com.vetos.modules.tenant.domain;

/** Şifre sıfırlama bağlantısını e-postayla gönderir. Gönderim hatası çağırana yansımaz (yalnız loglanır). */
public interface PasswordResetEmailPort {
    void sendResetLink(String email, String fullName, String resetUrl);
}
