package com.vetos.modules.inventory.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TARBIL takdim seklinden kullanim birimi ve paket buyuklugu tahmini (spec 2026-10-24 §3.4, 2026-10-25 §4.1).
 * <ul>
 *   <li>Asi: paket = kutudaki flakon ("Uretilen Doz-Kucuk Hayvan" kolonu, yoksa takdimdeki "N adet").</li>
 *   <li>Tablet/kapsul: TABLET, paket = kutudaki tablet ("N tabletlik" oncelikli); TARBIL'e tablet sayisi yazilir.</li>
 *   <li>Pipet/tup + ml: ML, parca = pipet kelimesine yakin "N ml'lik", paket = adet (en az 2) x parca. TARBIL pipeti kutunun
 *       toplam ml'siyle tutar (2026-10-25 canli: FIPROES KEDI 3 x 0,5 ml, acik 0,50). Dozaj/olcu pipeti parca degildir.</li>
 *   <li>Enjektor/siringa + ml: ML, paket = ml. Sise/flakon/tup ml/g: "N ml'lik" ifadesi duz "N ml"den once gelir.</li>
 *   <li>"1 ml'de 50 mg" gibi konsantrasyon ifadeleri hicbir yerde miktar sayilmaz (degerlendirme C2/I2).</li>
 * </ul>
 * Tahmin edilemeyen ADET; kullanici stok ekraninda duzeltir.
 */
public final class PresentationUnitGuesser {

    public record UnitGuess(StockUnit unit, BigDecimal packageSize, BigDecimal pieceSize, String pieceLabel) {
        public UnitGuess(StockUnit unit, BigDecimal packageSize) {
            this(unit, packageSize, null, null);
        }
    }

    private static final UnitGuess ADET = new UnitGuess(StockUnit.ADET, null);
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final String NUM = "(?<![\\d.,])(\\d+(?:[.,]\\d+)?)";
    private static final String INT = "(?<![\\d.,])(\\d+)";
    /** "ml" ama "ml'de" (konsantrasyon) degil. */
    private static final String ML = "ml(?!\\s*['’]?\\s*de(?![a-zçğıöşü]))";
    private static final String LIK = "\\s*[.'’]?\\s*(?:lik|lık|luk|lük|li|lı)(?![a-zçğıöşü])";
    private static final String NOT_ANOTHER_ML = "(?:(?!\\d+(?:[.,]\\d+)?\\s*ml).)";

    private static final Pattern TABLET_WORD = Pattern.compile("tablet|kapsül|kapsul");
    private static final Pattern[] TABLET_COUNTS = {
        Pattern.compile(INT + "\\s*(?:tablet|kapsül|kapsul)(?:lik|lük|luk|lık)"),
        Pattern.compile(INT + "\\s*(?:adet\\s*)?(?:tablet|kapsül|kapsul)"),
    };
    private static final Pattern PIECE = Pattern.compile("pipet|tüp|tup");
    private static final Pattern MEASURING = Pattern.compile("(?:dozaj|ölçü|ölcü|olcu|ölçek|olcek)\\s*(?:pipet|kaşı|kasi)");
    private static final Pattern PIECE_ML_BEFORE = Pattern.compile(NUM + "\\s*" + ML + "(?:" + LIK + ")?" + NOT_ANOTHER_ML + "{0,50}?(?:pipet|tüp|tup)");
    private static final Pattern PIECE_ML_AFTER = Pattern.compile("(?:pipet|tüp|tup)" + NOT_ANOTHER_ML + "{0,30}?" + NUM + "\\s*" + ML);
    private static final Pattern[] PIECE_COUNTS = {
        Pattern.compile(INT + "\\s*adetlik"),
        Pattern.compile(INT + "\\s*['’]\\s*(?:er|ar)\\s*adet"),
        Pattern.compile(INT + "\\s*(?:pipet|tüp|tup)"),
        Pattern.compile(INT + "\\s*adet"),
    };
    private static final Pattern SYRINGE = Pattern.compile("enjektör|enjektor|şırınga|siringa|sırınga");
    private static final Pattern CONTAINER = Pattern.compile("şişe|sise|flakon|tüp|tup");
    private static final Pattern AMOUNT_LIK = Pattern.compile(NUM + "\\s*(ml|gr|g)" + LIK);
    private static final Pattern AMOUNT = Pattern.compile(NUM + "\\s*(" + ML + "|gr|g)(?![a-zçğıöşü])");
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
        if (TABLET_WORD.matcher(text).find()) {
            Integer count = firstAtLeastTwo(text, TABLET_COUNTS);
            return count != null ? new UnitGuess(StockUnit.TABLET, BigDecimal.valueOf(count)) : ADET;
        }
        if (PIECE.matcher(text).find() && !MEASURING.matcher(text).find()) {
            BigDecimal piece = firstNumber(text, PIECE_ML_BEFORE);
            if (piece == null) {
                piece = firstNumber(text, PIECE_ML_AFTER);
            }
            Integer count = firstAtLeastTwo(text, PIECE_COUNTS);
            if (piece != null && piece.signum() > 0 && count != null) {
                String label = text.contains("pipet") ? "pipet" : "tüp";
                return new UnitGuess(StockUnit.ML, normalize(piece.multiply(BigDecimal.valueOf(count))), piece, label);
            }
        }
        if (SYRINGE.matcher(text).find()) {
            BigDecimal ml = firstMl(text);
            return ml != null ? new UnitGuess(StockUnit.ML, ml) : ADET;
        }
        if (!CONTAINER.matcher(text).find()) {
            return ADET;
        }
        Matcher m = AMOUNT_LIK.matcher(text);
        if (!m.find()) {
            m = AMOUNT.matcher(text);
            if (!m.find()) {
                return ADET;
            }
        }
        BigDecimal size = number(m.group(1));
        return size.signum() > 0 ? new UnitGuess(m.group(2).startsWith("ml") ? StockUnit.ML : StockUnit.G, size) : ADET;
    }

    /** Ilk "N ml'lik", yoksa ilk duz "N ml" ("ml'de" degil). */
    private static BigDecimal firstMl(String text) {
        Matcher m = AMOUNT_LIK.matcher(text);
        while (m.find()) {
            if (m.group(2).equals("ml")) {
                return positive(number(m.group(1)));
            }
        }
        m = AMOUNT.matcher(text);
        while (m.find()) {
            if (m.group(2).startsWith("ml")) {
                return positive(number(m.group(1)));
            }
        }
        return null;
    }

    /** Desenler oncelik sirasiyla; ilk >= 2 sayi (1 adet karton kutu gibi ifadeler paket sayisi degil). */
    private static Integer firstAtLeastTwo(String text, Pattern[] patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                int n = Integer.parseInt(m.group(1));
                if (n >= 2) {
                    return n;
                }
            }
        }
        return null;
    }

    private static BigDecimal firstNumber(String text, Pattern p) {
        Matcher m = p.matcher(text);
        return m.find() ? number(m.group(1)) : null;
    }

    private static BigDecimal positive(BigDecimal v) {
        return v.signum() > 0 ? v : null;
    }

    private static BigDecimal number(String s) {
        return normalize(new BigDecimal(s.replace(',', '.')));
    }

    /** 1.00 -> 1, 8.040 -> 8.04, 10 -> 10 (bilimsel gosterime dusmeden). */
    private static BigDecimal normalize(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }
}
