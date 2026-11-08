package com.vetos.modules.consentform.domain;

import com.vetos.modules.consentform.domain.exception.InvalidFormTemplateException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sablon icerigi kurallari. Ilk ihlalde Turkce mesajli InvalidFormTemplateException firlatir. */
public final class FormTemplateContentValidator {

    private static final Pattern CODE = Pattern.compile("[a-z0-9_]{1,40}");

    private FormTemplateContentValidator() {}

    public static void validate(FormTemplateContent c) {
        if (c == null) fail("Form içeriği boş");
        if (isBlank(c.title())) fail("Form başlığı boş olamaz");
        if (c.sections() == null || c.sections().isEmpty()) fail("Formda en az bir bölüm olmalı");

        Set<String> allCodes = new HashSet<>();
        int signatures = 0;
        for (FormSection section : c.sections()) {
            if (section == null || section.items() == null || section.items().isEmpty()) {
                fail("Her bölümde en az bir öğe olmalı");
            }
            for (FormItem item : section.items()) {
                if (item == null || item.type() == null) fail("Öğe türü eksik");
                if (item.type() == FormItemType.SIGNATURE) signatures++;
                validateItem(item, allCodes);
            }
            validatePlaceholders(section);
        }
        if (signatures != 1) fail("Formda tam olarak bir imza alanı olmalı");
    }

    private static void validateItem(FormItem item, Set<String> allCodes) {
        boolean block = item.type() == FormItemType.TEXT_BLOCK;
        if (block) {
            if (isBlank(item.text())) fail("Metin bloğunun metni boş olamaz");
            if (!isBlank(item.code())) fail("Metin bloğunun alan kodu olmamalı");
            if (item.binding() != null) fail("Metin bloğuna veri bağlantısı verilemez");
        } else {
            if (item.code() == null || !CODE.matcher(item.code()).matches()) {
                fail("Alan kodu geçersiz (yalnız a-z, 0-9, _; en fazla 40 karakter): " + item.code());
            }
            if (!allCodes.add(item.code())) fail("Alan kodu tekrar ediyor: " + item.code());
            if (isBlank(item.label())) fail("Alan etiketi boş olamaz: " + item.code());
            if (item.text() != null) fail("Yalnız metin bloğunda metin olabilir: " + item.code());
        }
        if (item.binding() != null) {
            if (item.type() == FormItemType.SIGNATURE) fail("İmza alanına veri bağlantısı verilemez");
            var binding = FormBinding.fromKey(item.binding());
            if (binding.isEmpty()) fail("Bilinmeyen veri bağlantısı: " + item.binding());
            if (!binding.orElseThrow().supports(item.type())) fail("Veri bağlantısı alan türüyle uyumsuz: " + item.binding());
        }
        if (item.unit() != null && item.type() != FormItemType.NUMBER) {
            fail("Yalnız sayı alanında birim olabilir: " + item.code());
        }
        if (item.type() == FormItemType.CHOICE) {
            List<String> options = item.options();
            if (options == null || options.size() < 2) fail("Seçimli alanda en az iki seçenek olmalı: " + item.code());
            if (options.stream().anyMatch(FormTemplateContentValidator::isBlank)) {
                fail("Seçenek boş olamaz: " + item.code());
            }
            if (new HashSet<>(options.stream().map(String::strip).toList()).size() != options.size()) {
                fail("Seçenekler tekrarlanamaz: " + item.code());
            }
        } else {
            if (item.options() != null) fail("Yalnız seçimli alanda seçenek olabilir: " + item.code());
            if (item.multiple()) fail("Yalnız seçimli alan çoklu olabilir: " + item.code());
        }
    }

    private static void validatePlaceholders(FormSection section) {
        Set<String> sectionCodes = new HashSet<>();
        for (FormItem i : section.items()) {
            if (i.type() != FormItemType.TEXT_BLOCK && i.type() != FormItemType.SIGNATURE && i.code() != null) sectionCodes.add(i.code());
        }
        for (FormItem i : section.items()) {
            if (i.type() != FormItemType.TEXT_BLOCK) continue;
            Matcher m = FormPlaceholders.PATTERN.matcher(i.text());
            while (m.find()) {
                if (!sectionCodes.contains(m.group(1))) {
                    fail("Metindeki {{" + m.group(1) + "}} aynı bölümdeki bir değer alanına karşılık gelmiyor");
                }
            }
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static void fail(String message) { throw new InvalidFormTemplateException(message); }
}
