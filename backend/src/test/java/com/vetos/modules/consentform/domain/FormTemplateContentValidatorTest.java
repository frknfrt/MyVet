package com.vetos.modules.consentform.domain;

import com.vetos.modules.consentform.domain.exception.InvalidFormTemplateException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormTemplateContentValidatorTest {

    private static FormItem text(String code, String label, String binding) {
        return new FormItem(FormItemType.SHORT_TEXT, code, label, null, false, binding, null, null, false);
    }

    private static FormItem signature() {
        return new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, null, null, null, false);
    }

    private static FormItem block(String t) {
        return new FormItem(FormItemType.TEXT_BLOCK, null, null, t, false, null, null, null, false);
    }

    private static FormTemplateContent content(String title, FormItem... items) {
        return new FormTemplateContent(title, List.of(new FormSection("Bölüm", List.of(items))));
    }

    private static void assertRejected(FormTemplateContent c, String messagePart) {
        assertThatThrownBy(() -> FormTemplateContentValidator.validate(c))
            .isInstanceOf(InvalidFormTemplateException.class)
            .hasMessageContaining(messagePart);
    }

    @Test
    void should_accept_minimalValidTemplate() {
        assertThatCode(() -> FormTemplateContentValidator.validate(
            content("Anestezi Onamı", text("ad_soyad", "Ad Soyad", "owner.fullName"), signature())
        )).doesNotThrowAnyException();
    }

    @Test
    void should_reject_whenNoSignature() {
        assertRejected(content("Form", text("ad", "Ad", null)), "tam olarak bir imza");
    }

    @Test
    void should_reject_whenTwoSignatures() {
        FormItem second = new FormItem(FormItemType.SIGNATURE, "imza2", "İmza 2", null, true, null, null, null, false);
        assertRejected(content("Form", signature(), second), "tam olarak bir imza");
    }

    @Test
    void should_reject_duplicateCodes() {
        assertRejected(content("Form", text("ad", "Ad", null), text("ad", "Ad 2", null), signature()),
            "Alan kodu tekrar ediyor: ad");
    }

    @Test
    void should_reject_invalidCodeFormat() {
        assertRejected(content("Form", text("Ad Soyad", "Ad", null), signature()), "Alan kodu geçersiz");
    }

    @Test
    void should_reject_unknownBinding() {
        assertRejected(content("Form", text("iban", "IBAN", "owner.iban"), signature()), "Bilinmeyen veri bağlantısı");
    }

    @Test
    void should_reject_bindingOnSignature() {
        FormItem sig = new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, "owner.fullName", null, null, false);
        assertRejected(content("Form", sig), "İmza alanına veri bağlantısı");
    }

    @Test
    void should_reject_choiceWithLessThanTwoOptions() {
        FormItem choice = new FormItem(FormItemType.CHOICE, "tercih", "Tercih", null, true, null, null, List.of("Evet"), false);
        assertRejected(content("Form", choice, signature()), "en az iki seçenek");
    }

    @Test
    void should_reject_placeholderToFieldInOtherSection() {
        FormTemplateContent c = new FormTemplateContent("Form", List.of(
            new FormSection("A", List.of(block("Klinik: {{klinik}}"), signature())),
            new FormSection("B", List.of(text("klinik", "Klinik", null)))
        ));
        assertRejected(c, "{{klinik}}");
    }

    @Test
    void should_accept_placeholderInSameSection() {
        FormTemplateContent c = content("Form", text("klinik", "Klinik", null), block("Klinik: {{klinik}}"), signature());
        assertThatCode(() -> FormTemplateContentValidator.validate(c)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_emptyTitle() {
        assertRejected(content("  ", text("ad", "Ad", null), signature()), "başlığı boş");
    }

    @Test
    void roundTrip_should_produceIdenticalJson() {
        FormTemplateContent c = content("Form",
            text("ad", "Ad", "owner.fullName"),
            new FormItem(FormItemType.CHOICE, "tercih", "Tercih", null, true, null, null, List.of("A", "B"), true),
            new FormItem(FormItemType.NUMBER, "kilo", "Kilo", null, false, "patient.weightKg", "kg", null, false),
            block("Metin"),
            signature());
        String json = FormTemplateContentCodec.toJson(c);
        assertThat(FormTemplateContentCodec.toJson(FormTemplateContentCodec.fromJson(json))).isEqualTo(json);
        assertThat(FormTemplateContentCodec.fromJson(json)).isEqualTo(c);
        assertThat(json).startsWith("{\"title\":\"Form\",\"sections\":[{\"title\":\"Bölüm\"");
    }

    @Test
    void fromJson_should_throwInvalidFormTemplate_whenMalformed() {
        assertThatThrownBy(() -> FormTemplateContentCodec.fromJson("{bozuk"))
            .isInstanceOf(InvalidFormTemplateException.class);
    }

    @Test
    void binding_should_resolveByKey_andMarkWritable() {
        assertThat(FormBinding.fromKey("owner.phone")).contains(FormBinding.OWNER_PHONE);
        assertThat(FormBinding.OWNER_PHONE.writable()).isTrue();
        assertThat(FormBinding.PATIENT_NAME.writable()).isFalse();
        assertThat(FormBinding.fromKey("nope")).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"İmza: {{imza}}", "İmza: {{ imza }}"})
    void should_reject_placeholderToSignature(String paragraph) {
        assertRejected(content("Form", block(paragraph), signature()), "{{imza}}");
    }

    @Test
    void should_accept_spacedPlaceholderToValueField() {
        assertThatCode(() -> FormTemplateContentValidator.validate(content("Form",
            text("ad", "Ad", null), block("Ad: {{ ad }}"), signature()))).doesNotThrowAnyException();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Evet", " Evet "})
    void should_reject_duplicateChoiceOptions(String second) {
        var choice = new FormItem(FormItemType.CHOICE, "tercih", "Tercih", null, false, null, null,
            List.of("Evet", second), true);
        assertRejected(content("Form", choice, signature()), "Seçenekler tekrarlanamaz");
    }

    @Test
    void should_accept_distinctCaseSensitiveOptions() {
        var choice = new FormItem(FormItemType.CHOICE, "tercih", "Tercih", null, false, null, null,
            List.of("A", "a"), true);
        assertThatCode(() -> FormTemplateContentValidator.validate(content("Form", choice, signature())))
            .doesNotThrowAnyException();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "CHOICE,owner.birthDate", "SHORT_TEXT,patient.weightKg", "NUMBER,owner.nationalId",
        "DATE,owner.phone", "PHONE,owner.fullName", "SHORT_TEXT,today", "CHOICE,owner.email"
    })
    void should_reject_incompatibleBinding(FormItemType type, String binding) {
        var field = new FormItem(type, "alan", "Alan", null, false, binding, null,
            type == FormItemType.CHOICE ? List.of("A", "B") : null, false);
        assertRejected(content("Form", field, signature()), "alan türüyle uyumsuz");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "DATE,owner.birthDate", "DATE,today", "NUMBER,patient.weightKg", "PHONE,owner.phone",
        "SHORT_TEXT,owner.secondaryPhone", "SHORT_TEXT,owner.nationalId", "LONG_TEXT,owner.address",
        "CHOICE,patient.species", "CHOICE,patient.sex", "CHOICE,patient.breed", "CHOICE,patient.color"
    })
    void should_accept_compatibleBinding(FormItemType type, String binding) {
        var field = new FormItem(type, "alan", "Alan", null, false, binding, null,
            type == FormItemType.CHOICE ? List.of("A", "B") : null, false);
        assertThatCode(() -> FormTemplateContentValidator.validate(content("Form", field, signature())))
            .doesNotThrowAnyException();
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> malformedStructures() {
        var sig = signature();
        return java.util.stream.Stream.of(
            org.junit.jupiter.params.provider.Arguments.of(null, "Form içeriği boş"),
            org.junit.jupiter.params.provider.Arguments.of(new FormTemplateContent("Form", null), "en az bir bölüm"),
            org.junit.jupiter.params.provider.Arguments.of(new FormTemplateContent("Form", List.of()), "en az bir bölüm"),
            org.junit.jupiter.params.provider.Arguments.of(new FormTemplateContent("Form", java.util.Arrays.asList((FormSection) null)), "Her bölümde"),
            org.junit.jupiter.params.provider.Arguments.of(new FormTemplateContent("Form", List.of(new FormSection(null, null))), "Her bölümde"),
            org.junit.jupiter.params.provider.Arguments.of(new FormTemplateContent("Form", List.of(new FormSection(null, List.of()))), "Her bölümde"),
            org.junit.jupiter.params.provider.Arguments.of(new FormTemplateContent("Form", List.of(new FormSection(null, java.util.Arrays.asList(null, sig)))), "Öğe türü eksik"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", new FormItem(null, "ad", "Ad", null, false, null, null, null, false), sig), "Öğe türü eksik"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", text("ad", " ", null), sig), "etiketi boş"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", text("a".repeat(41), "Ad", null), sig), "Alan kodu geçersiz"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", block(" "), sig), "metni boş"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", new FormItem(FormItemType.TEXT_BLOCK, "ad", null, "Metin", false, null, null, null, false), sig), "alan kodu olmamalı"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", new FormItem(FormItemType.TEXT_BLOCK, null, null, "Metin", false, "owner.fullName", null, null, false), sig), "Metin bloğuna veri"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", "Yanlış", false, null, null, null, false), sig), "Yalnız metin bloğunda"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, false, null, "kg", null, false), sig), "Yalnız sayı alanında"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", new FormItem(FormItemType.CHOICE, "ad", "Ad", null, false, null, null, List.of("A", " "), false), sig), "Seçenek boş"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, false, null, null, List.of("A", "B"), false), sig), "Yalnız seçimli alanda seçenek"),
            org.junit.jupiter.params.provider.Arguments.of(content("Form", new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, false, null, null, null, true), sig), "Yalnız seçimli alan çoklu")
        );
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("malformedStructures")
    void malformedStructuresAreDomainErrorsRatherThanNullPointerFailures(FormTemplateContent invalid, String message) {
        assertRejected(invalid, message);
    }
}
