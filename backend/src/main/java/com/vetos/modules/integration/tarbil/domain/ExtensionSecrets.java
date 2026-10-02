package com.vetos.modules.integration.tarbil.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** Eslestirme kodu ve eklenti anahtari uretimi. Ikisi de veritabaninda yalnizca SHA-256 ozeti olarak durur. */
public final class ExtensionSecrets {

    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ExtensionSecrets() {
    }

    public static String newPairingCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "vtx_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String normalizePairingCode(String raw) {
        String compact = raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        return compact.length() == 8 ? compact.substring(0, 4) + "-" + compact.substring(4) : compact;
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
