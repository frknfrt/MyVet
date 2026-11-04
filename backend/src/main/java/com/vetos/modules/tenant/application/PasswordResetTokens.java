package com.vetos.modules.tenant.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Sıfırlama bağlantısı için tahmin edilemez token ve veritabanında saklanan özeti. */
final class PasswordResetTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordResetTokens() {}

    /** 256 bit rastgele, URL'de güvenli (43 karakter). */
    static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 yok", e);
        }
    }
}
