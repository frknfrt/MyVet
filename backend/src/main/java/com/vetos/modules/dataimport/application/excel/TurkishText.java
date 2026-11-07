package com.vetos.modules.dataimport.application.excel;

import java.util.Locale;

/** Turkce metni karsilastirma icin sadelestirir: kucuk harf + ç→c ğ→g ı→i ö→o ş→s ü→u. */
public final class TurkishText {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private TurkishText() {}

    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toLowerCase(TR).toCharArray()) {
            sb.append(switch (ch) {
                case 'ç' -> 'c';
                case 'ğ' -> 'g';
                case 'ı', 'İ' -> 'i';
                case 'ö' -> 'o';
                case 'ş' -> 's';
                case 'ü' -> 'u';
                default -> ch;
            });
        }
        return sb.toString();
    }
}
