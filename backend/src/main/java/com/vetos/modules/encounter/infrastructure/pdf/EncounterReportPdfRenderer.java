package com.vetos.modules.encounter.infrastructure.pdf;

import com.vetos.modules.encounter.application.dto.EncounterReportData;
import com.vetos.modules.encounter.domain.DrugRoute;
import com.vetos.modules.encounter.domain.ExamFindingStatus;
import com.vetos.modules.encounter.domain.PhysicalExamFinding;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tek bir muayeneyi hasta sahibine yonelik "Muayene Raporu" PDF'ine cevirir.
 * OpenPDF'in gomme gerektirmeyen Cp1254 (Windows Turkce) kod sayfasi ile
 * standart Helvetica govdesi kullanilir -- boylece ayri bir TrueType font
 * dosyasi paketlemeye gerek kalmadan ğ/ş/ı/ö/ü/ç/İ dogru gosterilir.
 */
@Component
public class EncounterReportPdfRenderer {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ISTANBUL);

    public byte[] render(EncounterReportData data) {
        try {
            BaseFont baseFont = BaseFont.createFont(BaseFont.HELVETICA, "Cp1254", BaseFont.NOT_EMBEDDED);
            BaseFont boldBaseFont = BaseFont.createFont(BaseFont.HELVETICA_BOLD, "Cp1254", BaseFont.NOT_EMBEDDED);

            Font titleFont = new Font(boldBaseFont, 18, Font.NORMAL);
            Font clinicFont = new Font(boldBaseFont, 12, Font.NORMAL);
            Font sectionFont = new Font(boldBaseFont, 11, Font.NORMAL);
            Font labelFont = new Font(boldBaseFont, 9, Font.NORMAL);
            Font bodyFont = new Font(baseFont, 10, Font.NORMAL);
            Font mutedFont = new Font(baseFont, 8, Font.NORMAL);

            Document document = new Document(PageSize.A4, 42, 42, 48, 42);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph(data.clinicName(), clinicFont));
            Paragraph title = new Paragraph("Muayene Raporu", titleFont);
            title.setSpacingAfter(12);
            document.add(title);

            document.add(infoTable(data, labelFont, bodyFont));
            document.add(spacer());

            if (hasAnyVital(data)) {
                document.add(section("Vital Bulgular", sectionFont));
                document.add(new Paragraph(vitalsLine(data), bodyFont));
                document.add(spacer());
            }

            addTextSection(document, "Şikayet / Öykü", data.subjective(), sectionFont, bodyFont);
            addTextSection(document, "Muayene Bulguları", data.objective(), sectionFont, bodyFont);
            addExamFindings(document, data.physicalExamFindings(), sectionFont, bodyFont);
            addTextSection(document, "Tanı", data.assessment(), sectionFont, bodyFont);
            addTextSection(document, "Tedavi Planı", data.plan(), sectionFont, bodyFont);

            if (!data.prescriptionLines().isEmpty()) {
                document.add(section("Reçete Edilen İlaçlar", sectionFont));
                document.add(prescriptionTable(data.prescriptionLines(), labelFont, bodyFont));
                document.add(spacer());
            }

            Paragraph footer = new Paragraph(
                "Bu rapor " + data.clinicName() + " tarafından " + DATE_FMT.format(data.generatedAt())
                    + " tarihinde elektronik olarak oluşturulmuştur. Muayeneyi gerçekleştiren: " + data.staffName() + ".",
                mutedFont
            );
            footer.setSpacingBefore(18);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Muayene raporu PDF'i olusturulamadi", e);
        }
    }

    private PdfPTable infoTable(EncounterReportData data, Font labelFont, Font bodyFont) throws Exception {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setWidths(new float[]{1.1f, 2f, 1.1f, 2f});

        addInfoCell(table, "Hasta", data.patientName() + (blank(data.speciesName()) ? "" : " (" + data.speciesName() + ")"), labelFont, bodyFont);
        addInfoCell(table, "Sahip", data.ownerFullName(), labelFont, bodyFont);
        addInfoCell(table, "Muayene Tarihi", DATE_FMT.format(data.encounterDate()), labelFont, bodyFont);
        addInfoCell(table, "Hekim", data.staffName(), labelFont, bodyFont);
        return table;
    }

    private void addInfoCell(PdfPTable table, String label, String value, Font labelFont, Font bodyFont) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, labelFont));
        labelCell.setBorder(0);
        labelCell.setPaddingBottom(4);
        PdfPCell valueCell = new PdfPCell(new Paragraph(blank(value) ? "-" : value, bodyFont));
        valueCell.setBorder(0);
        valueCell.setPaddingBottom(4);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private boolean hasAnyVital(EncounterReportData data) {
        return data.weightKg() != null || data.temperatureC() != null || data.heartRate() != null || data.respiratoryRate() != null;
    }

    private String vitalsLine(EncounterReportData data) {
        List<String> parts = new java.util.ArrayList<>();
        if (data.weightKg() != null) parts.add("Ağırlık: " + formatDecimal(data.weightKg()) + " kg");
        if (data.temperatureC() != null) parts.add("Ateş: " + formatDecimal(data.temperatureC()) + " °C");
        if (data.heartRate() != null) parts.add("Nabız: " + data.heartRate() + " /dk");
        if (data.respiratoryRate() != null) parts.add("Solunum: " + data.respiratoryRate() + " /dk");
        return String.join("   •   ", parts);
    }

    private String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private void addTextSection(Document document, String title, String content, Font sectionFont, Font bodyFont) throws Exception {
        if (blank(content)) return;
        document.add(section(title, sectionFont));
        Paragraph body = new Paragraph(content, bodyFont);
        body.setSpacingAfter(10);
        document.add(body);
    }

    private void addExamFindings(Document document, List<PhysicalExamFinding> findings, Font sectionFont, Font bodyFont) throws Exception {
        List<PhysicalExamFinding> notable = findings.stream()
            .filter(f -> f.status() != ExamFindingStatus.NOT_EXAMINED)
            .toList();
        if (notable.isEmpty()) return;

        document.add(section("Fizik Muayene Bulguları", sectionFont));
        for (PhysicalExamFinding finding : notable) {
            String line = "• " + bodySystemLabel(finding.system()) + ": " + statusLabel(finding.status())
                + (blank(finding.note()) ? "" : " – " + finding.note());
            document.add(new Paragraph(line, bodyFont));
        }
        document.add(spacer());
    }

    private PdfPTable prescriptionTable(List<EncounterReportData.PrescriptionLine> lines, Font labelFont, Font bodyFont) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setWidths(new float[]{2.4f, 1.3f, 1.3f, 1f, 1.3f});

        for (String header : new String[]{"İlaç", "Doz", "Sıklık", "Süre", "Uygulama"}) {
            PdfPCell cell = new PdfPCell(new Paragraph(header, labelFont));
            cell.setPadding(5);
            table.addCell(cell);
        }
        for (EncounterReportData.PrescriptionLine line : lines) {
            table.addCell(cell(line.drugName(), bodyFont));
            table.addCell(cell(line.dosage(), bodyFont));
            table.addCell(cell(line.frequency(), bodyFont));
            table.addCell(cell(line.durationDays() + " gün", bodyFont));
            table.addCell(cell(routeLabel(line.route()), bodyFont));
        }
        return table;
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text == null ? "-" : text, font));
        cell.setPadding(5);
        return cell;
    }

    private Paragraph section(String title, Font sectionFont) {
        Paragraph p = new Paragraph(title, sectionFont);
        p.setSpacingBefore(8);
        p.setSpacingAfter(4);
        return p;
    }

    private Paragraph spacer() {
        return new Paragraph(new Chunk(" "));
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private String routeLabel(DrugRoute route) {
        return switch (route) {
            case ORAL -> "Ağızdan";
            case TOPICAL -> "Haricen";
            case INJECTABLE -> "Enjeksiyon";
        };
    }

    private String statusLabel(ExamFindingStatus status) {
        return switch (status) {
            case NORMAL -> "Normal";
            case ABNORMAL -> "Anormal";
            case NOT_EXAMINED -> "Muayene edilmedi";
        };
    }

    private String bodySystemLabel(com.vetos.modules.encounter.domain.ExamBodySystem system) {
        return switch (system) {
            case GENERAL_APPEARANCE -> "Genel Görünüm";
            case SKIN_COAT -> "Deri / Tüy";
            case EYES_EARS_MOUTH -> "Göz / Kulak / Ağız";
            case CARDIOVASCULAR -> "Kardiyovasküler";
            case RESPIRATORY -> "Solunum";
            case GASTROINTESTINAL -> "Gastrointestinal";
            case UROGENITAL -> "Ürogenital";
            case MUSCULOSKELETAL -> "Kas-İskelet";
            case NEUROLOGICAL -> "Nörolojik";
            case LYMPH_NODES -> "Lenf Nodları";
        };
    }
}
