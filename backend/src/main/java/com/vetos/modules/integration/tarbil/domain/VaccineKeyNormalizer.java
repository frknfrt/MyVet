package com.vetos.modules.integration.tarbil.domain;

import java.util.Locale;

/** "  KUDUZ   AŞISI " ve "Kuduz Aşısı" ayni esletirmeye dusmeli -- Turkce I/ı kurali dahil. */
public final class VaccineKeyNormalizer {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private VaccineKeyNormalizer() {
    }

    public static String normalize(String vaccineName) {
        if (vaccineName == null) {
            return "";
        }
        return vaccineName.trim().replaceAll("\\s+", " ").toLowerCase(TR);
    }
}
