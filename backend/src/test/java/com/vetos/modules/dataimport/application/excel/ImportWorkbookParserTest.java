package com.vetos.modules.dataimport.application.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vetos.modules.dataimport.domain.exception.InvalidImportFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ImportWorkbookParserTest {

    private final ImportWorkbookParser parser = new ImportWorkbookParser();

    private static Row row(Sheet s, int idx, String... cells) {
        Row r = s.createRow(idx);
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null) {
                r.createCell(i).setCellValue(cells[i]);
            }
        }
        return r;
    }

    private static ByteArrayInputStream toStream(XSSFWorkbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static final String[] PATIENT_HEADERS = {"kaynak_id", "sahip_kaynak_id", "ad", "tur"};

    private static XSSFWorkbook workbookWith(String[] ownerHeaders, String[] patientHeaders) {
        XSSFWorkbook wb = new XSSFWorkbook();
        if (ownerHeaders != null) {
            row(wb.createSheet("Sahipler"), 0, ownerHeaders);
        }
        if (patientHeaders != null) {
            row(wb.createSheet("Hastalar"), 0, patientHeaders);
        }
        return wb;
    }

    @Test
    void should_readTemplateItWrites() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ImportTemplateWriter().write(out);

        ParsedWorkbook parsed = parser.parse(new ByteArrayInputStream(out.toByteArray()));

        assertThat(parsed.structuralErrors()).isEmpty();
        assertThat(parsed.owners()).hasSize(1);
        assertThat(parsed.owners().get(0).rowNumber()).isEqualTo(2);
        assertThat(parsed.owners().get(0).values()).containsKeys("kaynak_id", "ad_soyad", "telefon");
        assertThat(parsed.patients()).hasSize(1);
        assertThat(parsed.patients().get(0).values()).containsKeys("kaynak_id", "sahip_kaynak_id", "ad", "tur");
    }

    @Test
    void should_normalizeHeaders() throws Exception {
        XSSFWorkbook wb = workbookWith(new String[] {"Kaynak ID", "AD SOYAD", "Telefon ", "İlçe", "Doğum Tarihi"}, PATIENT_HEADERS);
        row(wb.getSheet("Sahipler"), 1, "K1", "Ali Veli", "5321234567", "Çankaya", "x");

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        assertThat(parsed.structuralErrors()).isEmpty();
        assertThat(parsed.owners()).hasSize(1);
        assertThat(parsed.owners().get(0).values())
                .containsEntry("kaynak_id", "K1")
                .containsEntry("ad_soyad", "Ali Veli")
                .containsEntry("telefon", "5321234567")
                .containsEntry("ilce", "Çankaya");
        // dogum_tarihi Sahipler sablonunda yok -> orijinal basligiyla extras'a duser
        assertThat(parsed.owners().get(0).extras()).containsEntry("Doğum Tarihi", "x");
    }

    @Test
    void should_putUnknownColumnsIntoExtras() throws Exception {
        XSSFWorkbook wb = workbookWith(new String[] {"kaynak_id", "ad_soyad", "telefon", "Bakiye Notu"}, PATIENT_HEADERS);
        row(wb.getSheet("Sahipler"), 1, "K1", "Ali", "5321234567", "100 TL borc");

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        ParsedRow r = parsed.owners().get(0);
        assertThat(r.extras()).containsEntry("Bakiye Notu", "100 TL borc");
        assertThat(r.values()).doesNotContainKey("bakiye_notu");
    }

    @Test
    void should_convertDateCellsToIsoText() throws Exception {
        XSSFWorkbook wb = workbookWith(new String[] {"kaynak_id", "ad_soyad", "telefon"},
                new String[] {"kaynak_id", "sahip_kaynak_id", "ad", "tur", "dogum_tarihi", "notlar"});
        Row r = wb.getSheet("Hastalar").createRow(1);
        r.createCell(0).setCellValue("P1");
        r.createCell(1).setCellValue("K1");
        r.createCell(2).setCellValue("Pamuk");
        r.createCell(3).setCellValue("Kedi");
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd.mm.yyyy"));
        var dateCell = r.createCell(4);
        dateCell.setCellValue(Date.from(LocalDate.of(2019, 3, 5).atStartOfDay(ZoneId.systemDefault()).toInstant()));
        dateCell.setCellStyle(style);
        r.createCell(5).setCellValue("05.03.2019");

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        ParsedRow p = parsed.patients().get(0);
        assertThat(p.values()).containsEntry("dogum_tarihi", "2019-03-05").containsEntry("notlar", "05.03.2019");
    }

    @Test
    void should_keepNumericPhoneAsDigits() throws Exception {
        XSSFWorkbook wb = workbookWith(new String[] {"kaynak_id", "ad_soyad", "telefon"}, PATIENT_HEADERS);
        Row r = wb.getSheet("Sahipler").createRow(1);
        r.createCell(0).setCellValue(7);
        r.createCell(1).setCellValue("Ali");
        r.createCell(2).setCellValue(5321234567d);

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        assertThat(parsed.owners().get(0).values())
                .containsEntry("telefon", "5321234567")
                .containsEntry("kaynak_id", "7");
    }

    @Test
    void should_keepLongNumericIdsExact() throws Exception {
        XSSFWorkbook wb = workbookWith(new String[] {"kaynak_id", "ad_soyad", "telefon"},
                new String[] {"kaynak_id", "sahip_kaynak_id", "ad", "tur", "mikrocip_no"});
        Row o = wb.getSheet("Sahipler").createRow(1);
        o.createCell(0).setCellValue(123456789012d);
        o.createCell(1).setCellValue("Ali");
        o.createCell(2).setCellValue("5321234567");
        Row p = wb.getSheet("Hastalar").createRow(1);
        p.createCell(0).setCellValue(9876543210123d);
        p.createCell(1).setCellValue(123456789012d);
        p.createCell(2).setCellValue("Pamuk");
        p.createCell(3).setCellValue("Kedi");
        p.createCell(4).setCellValue(900164000123456d);

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        assertThat(parsed.owners().get(0).values()).containsEntry("kaynak_id", "123456789012");
        assertThat(parsed.patients().get(0).values())
                .containsEntry("kaynak_id", "9876543210123")
                .containsEntry("sahip_kaynak_id", "123456789012")
                .containsEntry("mikrocip_no", "900164000123456");
    }

    @Test
    void should_ignoreThousandsGroupingFormatOnNumbers() throws Exception {
        XSSFWorkbook wb = workbookWith(new String[] {"kaynak_id", "ad_soyad", "telefon", "notlar"}, PATIENT_HEADERS);
        Row r = wb.getSheet("Sahipler").createRow(1);
        r.createCell(0).setCellValue("K1");
        r.createCell(1).setCellValue("Ali");
        CellStyle grouped = wb.createCellStyle();
        grouped.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0"));
        var phone = r.createCell(2);
        phone.setCellValue(5321234567d);
        phone.setCellStyle(grouped);
        r.createCell(3).setCellValue(12.5d);

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        assertThat(parsed.owners().get(0).values())
                .containsEntry("telefon", "5321234567")
                .containsEntry("notlar", "12.5");
    }

    @Test
    void should_keepLongNumbersExactFromFormulaResultAndCachedFallback() throws Exception {
        XSSFWorkbook wb = workbookWith(new String[] {"kaynak_id", "ad_soyad", "telefon"},
                new String[] {"kaynak_id", "sahip_kaynak_id", "ad", "tur", "mikrocip_no", "pasaport_no"});
        Row o = wb.getSheet("Sahipler").createRow(1);
        o.createCell(0).setCellValue("K1");
        o.createCell(1).setCellValue("Ali");
        o.createCell(2).setCellValue("5321234567");
        Row p = wb.getSheet("Hastalar").createRow(1);
        p.createCell(0).setCellValue("P1");
        p.createCell(1).setCellValue("K1");
        p.createCell(2).setCellValue("Pamuk");
        p.createCell(3).setCellValue("Kedi");
        p.createCell(4).setCellFormula("900164000123000+456");
        var cached = (org.apache.poi.xssf.usermodel.XSSFCell) p.createCell(5);
        cached.setCellFormula("CUBEVALUE(\"x\")");
        cached.getCTCell().setV("900164000123457");

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        assertThat(parsed.patients().get(0).values())
                .containsEntry("mikrocip_no", "900164000123456")
                .containsEntry("pasaport_no", "900164000123457");
    }

    @Test
    void should_reportMissingSheetAndRequiredHeader() throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        row(wb.createSheet("Hastalar"), 0, "kaynak_id", "sahip_kaynak_id", "ad");

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        assertThat(parsed.structuralErrors())
                .contains("'Sahipler' sayfası yok", "'Hastalar' sayfasında 'tur' sütunu yok");
    }

    @Test
    void should_skipBlankRows() throws Exception {
        XSSFWorkbook wb = workbookWith(new String[] {"kaynak_id", "ad_soyad", "telefon"}, PATIENT_HEADERS);
        Sheet s = wb.getSheet("Sahipler");
        row(s, 1, "K1", "Ali", "5321234567");
        row(s, 2, " ", "", null);
        row(s, 4, "K2", "Veli", "5321234568");

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        assertThat(parsed.owners()).extracting(ParsedRow::rowNumber).containsExactly(2, 5);
    }

    @Test
    void should_fallBackToCachedValueWhenFormulaCannotBeEvaluated() throws Exception {
        XSSFWorkbook wb = workbookWith(new String[] {"kaynak_id", "ad_soyad", "telefon"}, PATIENT_HEADERS);
        Row r = wb.getSheet("Sahipler").createRow(1);
        r.createCell(0).setCellValue("K1");
        r.createCell(1).setCellValue("Ali");
        var f = (org.apache.poi.xssf.usermodel.XSSFCell) r.createCell(2);
        f.setCellFormula("CUBEVALUE(\"x\")");
        f.getCTCell().setV("5321234567");

        ParsedWorkbook parsed = parser.parse(toStream(wb));

        assertThat(parsed.structuralErrors()).isEmpty();
        assertThat(parsed.owners().get(0).values()).containsEntry("telefon", "5321234567");
    }

    @Test
    void should_throwOnNonXlsx() {
        byte[] junk = "bu bir excel dosyasi degil".getBytes();
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(junk)))
                .isInstanceOf(InvalidImportFileException.class);
    }
}
