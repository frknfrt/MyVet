package com.vetos.modules.integration.tarbil.domain;

import java.util.Locale;

/** Lot ve urun adini karsilastirma icin normalize eder: bosluklar sadelesir, Turkce buyuk harf. */
public final class StockMatchKey {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private StockMatchKey() {}

    public static String of(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(TR);
    }
}
