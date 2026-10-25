package com.vetos.modules.inventory.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TARBIL takdim seklinden kullanim birimi ve paket buyuklugu tahmini (spec 2026-10-24 §3.4). Asida paket = kutudaki
 * flakon ("Uretilen Doz-Kucuk Hayvan" kolonu, yoksa takdimdeki "N adet"). Ilacta sise/flakon/tup icindeki ml/g; pipet,
 * tablet, enjektor gibi bolunmeden kullanilanlar ADET. Tahmin edilemeyen ADET; kullanici stok ekraninda duzeltir.
 */
public final class PresentationUnitGuesser {

    public record UnitGuess(StockUnit unit, BigDecimal packageSize) {}

    private static final UnitGuess ADET = new UnitGuess(StockUnit.ADET, null);
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final Pattern WHOLE_UNITS = Pattern.compile("pipet|tablet|kapsül|kapsul|blister|enjektör|enjektor|şırınga|siringa");
    private static final Pattern CONTAINER = Pattern.compile("şişe|sise|flakon|tüp|tup");
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(ml|gr|g)(?![a-zçğıöşü])");
    private static final Pattern VACCINE_COUNT = Pattern.compile("(\\d+)\\s*adet");

    private PresentationUnitGuesser() {}

    public static UnitGuess guess(String tarbilSystem, String presentation, Integer dosesPerPackage) {
        String text = presentation == null ? "" : presentation.toLowerCase(TR);
        if ("HBSAPP_VACCINE".equals(tarbilSystem)) {
            if (dosesPerPackage != null && dosesPerPackage > 0) {
                return new UnitGuess(StockUnit.FLAKON, BigDecimal.valueOf(dosesPerPackage));
            }
            Matcher m = VACCINE_COUNT.matcher(text);
            return m.find() ? new UnitGuess(StockUnit.FLAKON, new BigDecimal(m.group(1))) : ADET;
        }
        if (WHOLE_UNITS.matcher(text).find() || !CONTAINER.matcher(text).find()) {
            return ADET;
        }
        Matcher m = AMOUNT.matcher(text);
        if (!m.find()) {
            return ADET;
        }
        BigDecimal size = new BigDecimal(m.group(1).replace(',', '.'));
        return size.signum() > 0 ? new UnitGuess(m.group(2).equals("ml") ? StockUnit.ML : StockUnit.G, size) : ADET;
    }
}
