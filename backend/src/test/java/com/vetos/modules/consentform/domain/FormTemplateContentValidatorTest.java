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
}
