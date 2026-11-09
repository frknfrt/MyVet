package com.vetos.modules.consentform.infrastructure.pdf;

import com.vetos.modules.consentform.domain.ConsentFormPdfData;
import com.vetos.modules.consentform.domain.FormItem;
import com.vetos.modules.consentform.domain.FormItemType;
import com.vetos.modules.consentform.domain.FormSection;
import com.vetos.modules.consentform.domain.FormTemplateContent;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentFormPdfRendererTest {

    private final ConsentFormPdfRenderer renderer = new ConsentFormPdfRenderer();

    private static FormItem field(FormItemType type, String code, String label) {
        return new FormItem(type, code, label, null, false, null, null, null, false);
    }

    private static FormItem text(String t) {
        return new FormItem(FormItemType.TEXT_BLOCK, null, null, t, false, null, null, null, false);
    }

    private static FormItem choice(String code, String label, List<String> options) {
        return new FormItem(FormItemType.CHOICE, code, label, null, false, null, null, options, false);
    }

    private static FormTemplateContent content() {
        return new FormTemplateContent("Şablon Onam Formu", List.of(new FormSection("Hasta Bilgisi", List.of(
            text("Ben {{ad}} işlemi kabul ediyorum. ğüşıöç"),
            field(FormItemType.SHORT_TEXT, "ad", "Ad"),
            choice("tur", "Tür", List.of("Kedi", "Köpek")),
            field(FormItemType.SIGNATURE, "imza", "İmza")))));
    }

    private static ConsentFormPdfData signed(Map<String, Object> values, String signerLine, boolean voided, String voidReason,
                                             boolean integrityOk, FormTemplateContent content) {
        return new ConsentFormPdfData("Vetly Klinik", "Şablon Onam Formu", content, values, signerLine,
            Instant.parse("2026-11-07T09:30:00Z"), null, "abcdef0123456789", voided, voidReason, integrityOk);
    }

    private static String extract(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int p = 1; p <= reader.getNumberOfPages(); p++) sb.append(extractor.getTextFromPage(p)).append('\n');
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private static Map<String, Object> vals() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ad", "Pamuk");
        v.put("tur", List.of("Kedi"));
        return v;
    }

    @Test
    void should_containTurkishCharacters() throws Exception {
        String text = extract(renderer.render(signed(vals(), "İmzalayan: Ayşe (hasta sahibi)", false, null, true, content())));

        assertThat(text).contains("İmza", "Şablon", "ğüşıöç");
        assertThat(text).contains("[X] Kedi", "[ ] Köpek");
    }

    @Test
    void should_fillInlinePlaceholder() throws Exception {
        String text = extract(renderer.render(signed(vals(), "İmzalayan: Ayşe (hasta sahibi)", false, null, true, content())));
        assertThat(text).contains("Ben Pamuk işlemi kabul ediyorum.").doesNotContain("{{ad}}");

        String blank = extract(renderer.render(signed(Map.of(), "x", false, null, true, content())));
        assertThat(blank).contains("Ben …………… işlemi kabul ediyorum.");
    }

    @Test
    void should_printOtherSignerLine() throws Exception {
        String line = "İmzalayan: Mehmet Demir (hasta yakını/diğer), TC: 12345678901";
        String text = extract(renderer.render(signed(vals(), line, false, null, true, content())));
        assertThat(text).contains(line);
    }

    @Test
    void should_stampVoided() throws Exception {
        String text = extract(renderer.render(signed(vals(), "x", true, "Yanlış hasta", true, content())));
        assertThat(text).contains("İPTAL EDİLDİ — Yanlış hasta");
    }

    @Test
    void should_printDigestShort() throws Exception {
        String text = extract(renderer.render(signed(vals(), "x", false, null, true, content())));
        assertThat(text).contains("Belge özeti: abcdef0123456789").doesNotContain("UYARI");
    }

    @Test
    void should_warn_whenIntegrityFails() throws Exception {
        String text = extract(renderer.render(signed(vals(), "x", false, null, false, content())));
        assertThat(text).contains("UYARI: Belge bütünlüğü doğrulanamadı");
    }

    @Test
    void should_renderShortNotice_whenContentCorrupted() throws Exception {
        String text = extract(renderer.render(signed(null, "İmzalayan: Ayşe (hasta sahibi)", false, null, false, null)));
        assertThat(text).contains("Bu belgenin içeriği doğrulanamadı", "İmzalayan: Ayşe (hasta sahibi)");
    }

    @Test
    void should_renderBlankTemplate_withoutDigest() throws Exception {
        ConsentFormPdfData blank = new ConsentFormPdfData("Vetly Klinik", "Şablon Onam Formu", content(), Map.of(),
            null, null, null, null, false, null, true);
        String text = extract(renderer.render(blank));
        assertThat(text).contains("Ben …………… işlemi", "[ ] Kedi", "[ ] Köpek").doesNotContain("Belge özeti");
    }

    private static byte[] tinyPng() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private static ConsentFormPdfData withSignature(byte[] png) {
        return new ConsentFormPdfData("Vetly Klinik", "Şablon Onam Formu", content(), vals(), "İmzalayan: Ayşe (hasta sahibi)",
            Instant.parse("2026-11-07T09:30:00Z"), png, "abcdef0123456789", false, null, true);
    }

    private static boolean hasImage(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            org.openpdf.text.pdf.PdfDictionary xo = reader.getPageN(1).getAsDict(org.openpdf.text.pdf.PdfName.RESOURCES)
                .getAsDict(org.openpdf.text.pdf.PdfName.XOBJECT);
            return xo != null && !xo.getKeys().isEmpty();
        } finally {
            reader.close();
        }
    }

    @Test
    void should_embedSignatureImage_whenPngValid() throws Exception {
        byte[] pdf = renderer.render(withSignature(tinyPng()));
        assertThat(hasImage(pdf)).isTrue();
        assertThat(extract(pdf)).contains("İmzalayan: Ayşe (hasta sahibi)").doesNotContain("İmza görseli okunamadı");
    }

    @Test
    void should_notFail_whenSignatureBytesAreGarbage() throws Exception {
        byte[] pdf = renderer.render(withSignature(new byte[]{1, 2, 3, 4, 5}));
        assertThat(extract(pdf)).contains("İmza görseli okunamadı", "İmzalayan: Ayşe (hasta sahibi)");
    }

    @Test
    void shouldPreserveGreekCyrillicAndExtendedLatin() throws Exception {
        var values = vals(); values.put("ad", "Жанна Ω Łódź");
        byte[] pdf = renderer.render(signed(values, "İmzalayan: Жанна Ω Łódź", false, null, true, content()));
        assertThat(extract(pdf)).contains("Жанна", "Ω", "Łódź", "İmza");
        String sample = System.getProperty("consent.pdf.sample");
        if (sample != null) {
            var path = java.nio.file.Path.of(sample);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, pdf);
        }
    }

    @Test
    void unsupportedGlyphMustBeExplicitErrorInsteadOfSilentlyDisappearing() {
        var values = vals(); values.put("ad", "漢字");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> renderer.render(signed(values, "x", false, null, true, content())))
            .isInstanceOf(com.vetos.modules.consentform.domain.exception.UnsupportedConsentPdfCharacterException.class)
            .hasMessageContaining("U+6F22");
    }

    @Test
    void shouldFillSpacedPlaceholdersAcceptedByValidator() throws Exception {
        var base = content();
        var sections = base.sections().stream().map(section -> new FormSection(section.title(),
            section.items().stream().map(item -> item.type() == FormItemType.TEXT_BLOCK
                ? new FormItem(item.type(), item.code(), item.label(), item.text().replace("{{ad}}", "{{ ad }}"),
                    item.required(), item.binding(), item.unit(), item.options(), item.multiple()) : item).toList())).toList();
        var spaced = new FormTemplateContent(base.title(), sections);
        com.vetos.modules.consentform.domain.FormTemplateContentValidator.validate(spaced);
        String text = extract(renderer.render(signed(vals(), "x", false, null, true, spaced)));
        assertThat(text).contains("Ben Pamuk işlemi").doesNotContain("{{ ad }}");
    }
    @Test
    void printsExactPrivacySnapshotBeforeSignature_andIndependentChannels() throws Exception {
        var original=content();
        var notice=com.vetos.modules.consentform.domain.PrivacyNoticeAcknowledgement.of(
            new com.vetos.modules.consentform.domain.PrivacyNotice(3,"Veri sorumlusu: Test Klinik\n\nİletişim ve başvuru: klinik@example.test"),true,false,true);
        var signedContent=new FormTemplateContent(original.title(),original.sections(),notice,true);
        byte[] pdf=renderer.render(signed(vals(),"İmzalayan: Ayşe (hasta sahibi)",false,null,true,signedContent));
        String text=extract(pdf);
        assertThat(text).contains("KVKK Aydınlatma Metni", "sürüm 3", notice.text().split("\n")[0],"klinik@example.test",
            "[X] "+notice.statement(),"SMS Evet / WhatsApp Hayır / E-posta Evet");
        assertThat(text.indexOf("KVKK Aydınlatma Metni")).isLessThan(text.indexOf("İmzalayan:"));
        if(System.getProperty("kvkk.pdf.sample")!=null) java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("kvkk.pdf.sample")),pdf);
    }

    @Test
    void fullCommonNoticeIsNotTruncated_whenItSpansPages() throws Exception {
        var original=content();
        var notice=com.vetos.modules.consentform.domain.PrivacyNoticeAcknowledgement.of(
            com.vetos.modules.consentform.domain.CommonPrivacyNotice.forClinic("Örnek Veteriner Kliniği"),false,false,false);
        byte[] pdf=renderer.render(signed(vals(),"İmzalayan: Ayşe (hasta sahibi)",false,null,true,
            new FormTemplateContent(original.title(),original.sections(),notice,true)));
        var reader=new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            String lastPage = new PdfTextExtractor(reader).getTextFromPage(reader.getNumberOfPages());
            assertThat(lastPage).contains("İmza\n", "İmzalayan: Ayşe", "İmza tarihi:");
        } finally {reader.close();}
        assertThat(extract(pdf)).contains("Örnek Veteriner Kliniği","SMS Hayır / WhatsApp Hayır / E-posta Hayır", "İmzalayan: Ayşe");
        if(System.getProperty("kvkk.pdf.long.sample")!=null) java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("kvkk.pdf.long.sample")),pdf);
    }

}
