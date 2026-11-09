package com.vetos.modules.consentform.infrastructure.pdf;

import com.vetos.modules.consentform.domain.ConsentFormPdfData;
import com.vetos.modules.consentform.domain.ConsentFormPdfPort;
import com.vetos.modules.consentform.domain.FormItem;
import com.vetos.modules.consentform.domain.FormItemType;
import com.vetos.modules.consentform.domain.FormSection;
import com.vetos.modules.consentform.domain.FormPlaceholders;
import com.vetos.modules.consentform.domain.exception.UnsupportedConsentPdfCharacterException;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Onay formunu gömülü DejaVu Sans / Identity-H ile üretir.
 * Desteklenmeyen karakterleri sessizce düşürmek yerine açıklayıcı hata verir.
 */
@Component
public class ConsentFormPdfRenderer implements ConsentFormPdfPort {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ConsentFormPdfRenderer.class);
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ISTANBUL);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final BaseFont PDF_FONT = loadFont();
    private static final String BLANK = "……………";

    private static BaseFont loadFont() {
        try (var stream = ConsentFormPdfRenderer.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (stream == null) throw new IllegalStateException("PDF font resource missing");
            return BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, stream.readAllBytes(), null);
        } catch (Exception e) {
            throw new IllegalStateException("PDF Unicode font could not be loaded", e);
        }
    }

    private static void checkText(Object value) {
        if (value instanceof List<?> values) { values.forEach(ConsentFormPdfRenderer::checkText); return; }
        if (value == null) return;
        String.valueOf(value).codePoints().forEach(cp -> {
            if (cp != '\n' && cp != '\r' && cp != '\t' && !PDF_FONT.charExists(cp)) {
                throw new UnsupportedConsentPdfCharacterException(cp);
            }
        });
    }

    private static void validateCharacters(ConsentFormPdfData data) {
        checkText(data.clinicName()); checkText(data.title()); checkText(data.signerLine());
        checkText(data.voidReason()); checkText(data.digestShort());
        if (data.values() != null) data.values().values().forEach(ConsentFormPdfRenderer::checkText);
        if (data.content() != null && data.content().privacyNotice() != null) {
            checkText(data.content().privacyNotice().text());checkText(data.content().privacyNotice().statement());
        }
        if (data.content() != null) for (FormSection section : data.content().sections()) {
            checkText(section.title());
            for (FormItem item : section.items()) { checkText(item.label()); checkText(item.text()); checkText(item.options()); checkText(item.unit()); }
        }
    }

    @Override
    public byte[] render(ConsentFormPdfData data) {
        validateCharacters(data);
        try {
            BaseFont baseFont = PDF_FONT;
            BaseFont boldBaseFont = PDF_FONT;

            Font titleFont = new Font(boldBaseFont, 16, Font.BOLD);
            Font clinicFont = new Font(boldBaseFont, 12, Font.BOLD);
            Font sectionFont = new Font(boldBaseFont, 11, Font.BOLD);
            Font labelFont = new Font(boldBaseFont, 10, Font.BOLD);
            Font bodyFont = new Font(baseFont, 10, Font.NORMAL);
            Font mutedFont = new Font(baseFont, 8, Font.NORMAL);
            Font voidFont = new Font(boldBaseFont, 11, Font.NORMAL, new Color(0xB0, 0x1E, 0x1E));
            Font warnFont = new Font(boldBaseFont, 10, Font.NORMAL, new Color(0xB4, 0x53, 0x09));

            Document document = new Document(PageSize.A4, 42, 42, 48, 42);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph(data.clinicName(), clinicFont));
            Paragraph title = new Paragraph(data.title(), titleFont);
            title.setSpacingAfter(8);
            document.add(title);

            if (data.voided()) {
                String reason = data.voidReason() == null || data.voidReason().isBlank() ? "" : " — " + data.voidReason();
                Paragraph voided = new Paragraph("İPTAL EDİLDİ" + reason, voidFont);
                voided.setSpacingAfter(8);
                document.add(voided);
            }

            if (data.content() == null) {
                document.add(new Paragraph("Bu belgenin içeriği doğrulanamadı", warnFont));
                addSignatureMeta(document, data, bodyFont);
                addDigest(document, data, mutedFont);
                document.close();
                return out.toByteArray();
            }

            if (!data.integrityOk()) {
                Paragraph warn = new Paragraph("UYARI: Belge bütünlüğü doğrulanamadı", warnFont);
                warn.setSpacingAfter(8);
                document.add(warn);
            }

            Map<String, Object> values = data.values() == null ? Map.of() : data.values();
            for (FormSection section : data.content().sections()) {
                if (section.title() != null && !section.title().isBlank()) {
                    Paragraph st = new Paragraph(section.title(), sectionFont);
                    st.setSpacingBefore(10);
                    st.setSpacingAfter(4);
                    document.add(st);
                }
                Map<String, FormItem> byCode = new HashMap<>();
                for (FormItem item : section.items()) {
                    if (item.code() != null) byCode.put(item.code(), item);
                }
                for (FormItem item : section.items()) {
                    addItem(document, item, byCode, values, data, labelFont, bodyFont);
                }
            }

            addDigest(document, data, mutedFont);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Onay formu PDF'i olusturulamadi", e);
        }
    }

    private void addItem(Document document, FormItem item, Map<String, FormItem> byCode, Map<String, Object> values,
                         ConsentFormPdfData data, Font labelFont, Font bodyFont) throws Exception {
        switch (item.type()) {
            case TEXT_BLOCK -> {
                Paragraph p = new Paragraph(fillPlaceholders(item.text(), byCode, values), bodyFont);
                p.setSpacingAfter(6);
                document.add(p);
            }
            case SIGNATURE -> addSignature(document, item, data, labelFont, bodyFont);
            default -> {
                Paragraph p = new Paragraph();
                p.add(new Chunk(item.label() + ": ", labelFont));
                p.add(new Chunk(displayValue(item, values.get(item.code())), bodyFont));
                p.setSpacingAfter(4);
                document.add(p);
            }
        }
    }

    private String fillPlaceholders(String text, Map<String, FormItem> byCode, Map<String, Object> values) {
        if (text == null) return "";
        Matcher m = FormPlaceholders.PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            FormItem target = byCode.get(m.group(1));
            String v = target == null ? BLANK : displayValue(target, values.get(m.group(1)));
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Alan degerinin PDF metni; bos ise "……………". */
    private String displayValue(FormItem item, Object raw) {
        if (item.type() == FormItemType.CHOICE) {
            List<String> picked = raw instanceof List<?> l ? l.stream().map(String::valueOf).toList()
                : raw instanceof String s && !s.isBlank() ? List.of(s) : List.of();
            if (item.options() == null) return BLANK;
            List<String> parts = new ArrayList<>();
            for (String option : item.options()) {
                parts.add((picked.contains(option) ? "[X] " : "[ ] ") + option);
            }
            return String.join("  ", parts);
        }
        if (raw == null || String.valueOf(raw).isBlank()) return BLANK;
        String s = String.valueOf(raw);
        if (item.type() == FormItemType.DATE) {
            try {
                return DATE_FMT.format(LocalDate.parse(s));
            } catch (RuntimeException e) {
                return s;
            }
        }
        if (item.type() == FormItemType.NUMBER && item.unit() != null && !item.unit().isBlank()) {
            return s + " " + item.unit();
        }
        return s;
    }

    private void addSignature(Document document, FormItem item, ConsentFormPdfData data, Font labelFont, Font bodyFont) throws Exception {
        if (data.content() != null && data.content().privacyNotice() != null) {
            var notice = data.content().privacyNotice();
            Paragraph heading = new Paragraph("KVKK Aydınlatma Metni — sürüm " + notice.version(), labelFont);
            heading.setSpacingBefore(12);document.add(heading);
            for (String paragraph : notice.text().split("\\R\\s*\\R")) {
                Paragraph p = new Paragraph(paragraph,bodyFont);p.setSpacingAfter(6);document.add(p);
            }
            document.add(new Paragraph("[X] " + notice.statement(),bodyFont));
            document.add(new Paragraph("Hizmet bildirim tercihleri (pazarlama izni değildir): SMS " + (notice.sms() ? "Evet" : "Hayır")
                + " / WhatsApp " + (notice.whatsapp() ? "Evet" : "Hayır") + " / E-posta " + (notice.email() ? "Evet" : "Hayır"),bodyFont));
        }
        Paragraph label = new Paragraph(item.label(), labelFont);
        label.setSpacingBefore(14);
        label.setSpacingAfter(4);
        PdfPTable signatureBlock = new PdfPTable(1);
        signatureBlock.setWidthPercentage(100);
        signatureBlock.setKeepTogether(true);
        signatureBlock.setSplitRows(false);
        PdfPCell signatureCell = new PdfPCell();
        signatureCell.setBorder(PdfPCell.NO_BORDER);
        signatureCell.setPadding(0);
        signatureCell.addElement(label);

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(50);
        box.setHorizontalAlignment(Element.ALIGN_LEFT);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(90);
        cell.setPadding(4);
        cell.setBorderColor(Color.GRAY);
        if (data.signaturePng() != null) {
            try {
                Image img = Image.getInstance(data.signaturePng());
                img.scaleToFit(220, 80);
                cell.addElement(img);
            } catch (Exception e) {
                // bozuk PNG: 500 yerine bos kutu + not
                log.warn("Imza gorseli okunamadi: {}", e.toString());
                cell.addElement(new Paragraph("İmza görseli okunamadı", bodyFont));
            }
        }
        box.addCell(cell);
        signatureCell.addElement(box);
        if (data.signerLine() != null) {
            Paragraph signer = new Paragraph(data.signerLine(), bodyFont);
            signer.setSpacingBefore(4);
            signatureCell.addElement(signer);
        }
        if (data.signedAt() != null) {
            signatureCell.addElement(new Paragraph("İmza tarihi: " + DATE_TIME_FMT.format(data.signedAt()), bodyFont));
        }
        signatureBlock.addCell(signatureCell);
        document.add(signatureBlock);
    }

    private void addSignatureMeta(Document document, ConsentFormPdfData data, Font bodyFont) throws Exception {
        if (data.signerLine() != null) {
            Paragraph signer = new Paragraph(data.signerLine(), bodyFont);
            signer.setSpacingBefore(4);
            document.add(signer);
        }
        if (data.signedAt() != null) {
            document.add(new Paragraph("İmza tarihi: " + DATE_TIME_FMT.format(data.signedAt()), bodyFont));
        }
    }

    private void addDigest(Document document, ConsentFormPdfData data, Font mutedFont) throws Exception {
        if (data.digestShort() == null) return;
        Paragraph footer = new Paragraph("Belge özeti: " + data.digestShort(), mutedFont);
        footer.setSpacingBefore(18);
        document.add(footer);
    }
}
