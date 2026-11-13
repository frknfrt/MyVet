package com.vetos.modules.platformadmin.domain;

/** Turkiye cep telefonu normallestirme: farkli yazimlar tek bir E.164 degerine iner. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * @return +905XXXXXXXXX
     * @throws IllegalArgumentException numara bir Turkiye cep numarasi degilse
     */
    public static String toE164Tr(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Telefon bos olamaz");
        }
        String trimmed = raw.trim();
        boolean hasPlus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");
        String national;
        if (hasPlus) {
            if (!digits.startsWith("90")) {
                throw new IllegalArgumentException("Yalnizca Turkiye numaralari desteklenir");
            }
            national = digits.substring(2);
        } else if (digits.length() == 12 && digits.startsWith("90")) {
            national = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            national = digits.substring(1);
        } else {
            national = digits;
        }
        if (national.length() != 10 || national.charAt(0) != '5') {
            throw new IllegalArgumentException("Gecerli bir cep telefonu numarasi degil");
        }
        return "+90" + national;
    }
}
