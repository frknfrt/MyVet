package com.vetos.modules.integration.tarbil.domain;

import java.util.Locale;

/**
 * "  KUDUZ   AŞISI ", "Kuduz Aşısı" ve "NOBIVAC"/"Nobivac" ayni esletirmeye dusmeli. Turkce kucuk harf
 * I'yi ı yapar; Latin marka adlari bolunmesin diye ı sonra i'ye katlanir (I/ı/İ/i tek anahtar).
 */
public final class VaccineKeyNormalizer {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private VaccineKeyNormalizer() {
    }

    public static String normalize(String vaccineName) {
        if (vaccineName == null) {
            return "";
        }
        return vaccineName.trim().replaceAll("\\s+", " ").toLowerCase(TR).replace('ı', 'i');
    }
}
