package com.vetos.platform.observability;

import java.util.regex.Pattern;

/**
 * Loglara kisisel veri (telefon, e-posta, TCKN) acik yazilmasin diye satir bazinda
 * maskeleme yardimcilari (spec 2026-10-14 S4.5, 1. katman). LogPiiGuardTest, telefon/
 * e-posta/yanit govdesi tasiyan log argumanlarinin bu siniftan gecmesini zorunlu kilar.
 * elevenDigits ayni zamanda 2. katmanin (logback %replace, JSON customizer) tek tanimidir.
 */
public final class LogMasking {

    /** 11 haneli, oncesi/sonrasi rakam olmayan sayi: TCKN ve 05xx cep telefonu. */
    public static final String ELEVEN_DIGIT_REGEX = "(?<!\\d)(\\d{3})\\d{6}(\\d{2})(?!\\d)";
    public static final String ELEVEN_DIGIT_REPLACEMENT = "$1******$2";
    static final int BODY_LIMIT = 300;

    private static final Pattern ELEVEN_DIGITS = Pattern.compile(ELEVEN_DIGIT_REGEX);

    private LogMasking() {
    }

    public static String phone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 5) {
            return "***";
        }
        return digits.substring(0, 3) + "*".repeat(digits.length() - 5) + digits.substring(digits.length() - 2);
    }

    public static String email(String raw) {
        if (raw == null) {
            return null;
        }
        int at = raw.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return raw.charAt(0) + "*".repeat(at - 1) + raw.substring(at);
    }

    /** Dis servis yanit govdesi: 11 haneli sayilar maskelenir, ilk 300 karakterden fazlasi kesilir. */
    public static String body(String raw) {
        if (raw == null) {
            return null;
        }
        String masked = elevenDigits(raw);
        if (masked.length() <= BODY_LIMIT) {
            return masked;
        }
        return masked.substring(0, BODY_LIMIT) + "…(+" + (masked.length() - BODY_LIMIT) + " karakter)";
    }

    public static String elevenDigits(String text) {
        if (text == null || countDigits(text) < 11) {
            return text;
        }
        return ELEVEN_DIGITS.matcher(text).replaceAll(ELEVEN_DIGIT_REPLACEMENT);
    }

    private static int countDigits(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
