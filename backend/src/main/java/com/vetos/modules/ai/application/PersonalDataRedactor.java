package com.vetos.modules.ai.application;

import java.util.regex.Pattern;

/**
 * AI'ya giden hasta geçmişi metninden telefon, TC kimlik ve e-postayı gizler (2026-10-25).
 * Serbest metin alanlarına (kritik uyarı, lab özeti, görüntüleme bulgusu, muayene notu) yazılmış olabilecek
 * sahip bilgisi Claude'a gitmesin. İsimler güvenilir yakalanamadığı için gizlenmez; ekrandaki uyarı onu karşılar.
 */
final class PersonalDataRedactor {

    /** TR cep/sabit: isteğe bağlı +90/90, isteğe bağlı 0 ve parantez, 2xx-5xx alan kodu, 7 hane; boşluk/nokta/tire ayırıcı. */
    private static final Pattern PHONE = Pattern.compile(
        "(?<![\\d\\w])(?:\\+?90[\\s.-]?)?\\(?0?[2-5]\\d{2}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{2}[\\s.-]?\\d{2}(?!\\d)");
    private static final Pattern TC_ID = Pattern.compile("(?<!\\d)[1-9]\\d{10}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");

    private PersonalDataRedactor() {}

    static String redact(String text) {
        if (text == null) return null;
        String out = EMAIL.matcher(text).replaceAll("[e-posta gizlendi]");
        out = PHONE.matcher(out).replaceAll("[telefon gizlendi]");
        return TC_ID.matcher(out).replaceAll("[TC gizlendi]");
    }
}
