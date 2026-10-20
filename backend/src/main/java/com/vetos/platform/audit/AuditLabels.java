package com.vetos.platform.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

/** Olay etiketi/ozeti icin ortak bicimler. */
public final class AuditLabels {

    private AuditLabels() {
    }

    public static String shortId(UUID id) {
        return id == null ? null : id.toString().substring(0, 8);
    }

    public static String money(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("tr-TR")));
        return format.format(amount.setScale(2, RoundingMode.HALF_UP)) + " ₺";
    }
}
