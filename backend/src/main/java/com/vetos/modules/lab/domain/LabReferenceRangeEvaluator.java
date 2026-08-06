package com.vetos.modules.lab.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faz 2 kural tabanli on-degerlendirme (@docs/implementation-plan.md "Faz 1
 * Kapsam Disi" bolumu) -- referans araligi disindaki degerleri otomatik
 * HIGH/LOW olarak isaretler. API anahtari / dis servis gerektirmez; Faz 3'te
 * bu sinifin cagrildigi yer AiGatewayRouter uzerinden gercek LLM cagrisiyla
 * degistirilecek, sozlesme (EvaluateLabResultItemsUseCase) ayni kalacak.
 */
public final class LabReferenceRangeEvaluator {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");
    private static final Pattern RANGE = Pattern.compile(
        "^\\s*(-?\\d+(?:[.,]\\d+)?)\\s*[-–]\\s*(-?\\d+(?:[.,]\\d+)?)\\s*$"
    );
    private static final Pattern MAX_ONLY = Pattern.compile("^\\s*(?:<=|≤|<)\\s*(-?\\d+(?:[.,]\\d+)?)\\s*$");
    private static final Pattern MIN_ONLY = Pattern.compile("^\\s*(?:>=|≥|>)\\s*(-?\\d+(?:[.,]\\d+)?)\\s*$");

    private LabReferenceRangeEvaluator() {}

    /**
     * Deger ve referans araligi metinden sayisal olarak yorumlanabiliyorsa
     * LOW/HIGH/NORMAL doner; format taninmiyorsa (ornegin "Negatif" gibi
     * sayisal olmayan degerler) null doner -- bu durumda mevcut/manuel bayrak
     * korunur.
     */
    public static LabValueFlag evaluate(String value, String referenceRange) {
        if (value == null || referenceRange == null) return null;
        Double numericValue = parseLeadingNumber(value);
        if (numericValue == null) return null;

        Matcher rangeMatcher = RANGE.matcher(referenceRange);
        if (rangeMatcher.matches()) {
            double min = parseStrict(rangeMatcher.group(1));
            double max = parseStrict(rangeMatcher.group(2));
            if (min > max) {
                double tmp = min;
                min = max;
                max = tmp;
            }
            if (numericValue < min) return LabValueFlag.LOW;
            if (numericValue > max) return LabValueFlag.HIGH;
            return LabValueFlag.NORMAL;
        }

        Matcher maxMatcher = MAX_ONLY.matcher(referenceRange);
        if (maxMatcher.matches()) {
            double max = parseStrict(maxMatcher.group(1));
            return numericValue > max ? LabValueFlag.HIGH : LabValueFlag.NORMAL;
        }

        Matcher minMatcher = MIN_ONLY.matcher(referenceRange);
        if (minMatcher.matches()) {
            double min = parseStrict(minMatcher.group(1));
            return numericValue < min ? LabValueFlag.LOW : LabValueFlag.NORMAL;
        }

        return null;
    }

    private static double parseStrict(String raw) {
        return Double.parseDouble(raw.replace(',', '.'));
    }

    private static Double parseLeadingNumber(String raw) {
        Matcher m = NUMBER.matcher(raw.trim().replace(',', '.'));
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
