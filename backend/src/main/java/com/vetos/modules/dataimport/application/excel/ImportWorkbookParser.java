package com.vetos.modules.dataimport.application.excel;

import com.vetos.modules.dataimport.domain.exception.InvalidImportFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Aktarim .xlsx dosyasini okur: sayfa/baslik dogrular, satirlari {@link ParsedRow}'a cevirir. */
@org.springframework.stereotype.Component
public class ImportWorkbookParser {

    private static final Locale TR = Locale.forLanguageTag("tr");

    public ParsedWorkbook parse(InputStream xlsx) {
        byte[] bytes;
        try {
            bytes = xlsx.readAllBytes();
        } catch (IOException e) {
            throw new InvalidImportFileException("Dosya okunamadı", e);
        }
        // .xlsx bir ZIP'tir ("PK"); eski .xls ya da rastgele bayt burada elenir.
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new InvalidImportFileException("Dosya geçerli bir .xlsx değil");
        }
        Workbook wb;
        try {
            wb = new XSSFWorkbook(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new InvalidImportFileException("Dosya geçerli bir .xlsx değil", e);
        }
        try (wb) {
            List<String> errors = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(TR);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            List<ParsedRow> owners = readSheet(wb, ImportTemplateColumns.OWNERS_SHEET,
                    ImportTemplateColumns.OWNER_COLUMNS, ImportTemplateColumns.OWNER_REQUIRED, formatter, evaluator, errors);
            List<ParsedRow> patients = readSheet(wb, ImportTemplateColumns.PATIENTS_SHEET,
                    ImportTemplateColumns.PATIENT_COLUMNS, ImportTemplateColumns.PATIENT_REQUIRED, formatter, evaluator,
                    errors);
            return new ParsedWorkbook(owners, patients, errors);
        } catch (IOException e) {
            throw new InvalidImportFileException("Dosya okunamadı", e);
        }
    }

    private List<ParsedRow> readSheet(Workbook wb, String sheetName, List<String> templateColumns,
            List<String> required, DataFormatter formatter, FormulaEvaluator evaluator, List<String> errors) {
        Sheet sheet = findSheet(wb, sheetName);
        if (sheet == null) {
            errors.add("'" + sheetName + "' sayfası yok");
            return List.of();
        }
        Row headerRow = sheet.getRow(0);
        // sutun indeksi -> {normalize ad, orijinal baslik}
        Map<Integer, String[]> columns = new LinkedHashMap<>();
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                String original = cellText(cell, formatter, evaluator);
                if (original == null) {
                    continue;
                }
                columns.put(cell.getColumnIndex(), new String[] {normalizeHeader(original), original});
            }
        }
        for (String req : required) {
            boolean present = columns.values().stream().anyMatch(c -> c[0].equals(req));
            if (!present) {
                errors.add("'" + sheetName + "' sayfasında '" + req + "' sütunu yok");
            }
        }

        List<ParsedRow> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            Map<String, String> extras = new LinkedHashMap<>();
            for (Map.Entry<Integer, String[]> col : columns.entrySet()) {
                String text = cellText(row.getCell(col.getKey()), formatter, evaluator);
                if (text == null) {
                    continue;
                }
                if (templateColumns.contains(col.getValue()[0])) {
                    values.putIfAbsent(col.getValue()[0], text);
                } else {
                    extras.put(col.getValue()[1], text);
                }
            }
            if (values.isEmpty() && extras.isEmpty()) {
                continue;
            }
            rows.add(new ParsedRow(i + 1, values, extras));
        }
        return rows;
    }

    private static Sheet findSheet(Workbook wb, String name) {
        String wanted = normalizeHeader(name);
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (normalizeHeader(wb.getSheetName(i)).equals(wanted)) {
                return wb.getSheetAt(i);
            }
        }
        return null;
    }

    /** Hucre metni (kirpilmis); bos ise null. Tarih bicimli sayisal hucreler yyyy-MM-dd olur. */
    private static String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        boolean cachedFallback = false;
        if (type == CellType.FORMULA) {
            try {
                type = evaluator.evaluateFormulaCell(cell);
            } catch (RuntimeException e) {
                // Degerlendirilemeyen formul (desteklenmeyen fonksiyon, dis baglanti vb.): dosyadaki onbellek degeri kullanilir.
                type = cell.getCachedFormulaResultType();
                cachedFallback = true;
            }
        }
        String text;
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            text = cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } else {
            try {
                if (type == CellType.NUMERIC) {
                    // Excel bicimi (General -> 9,00164E+14; #,##0 -> 5.321.234.567) kullanilmaz: mikrocip, kaynak_id,
                    // telefon gibi uzun sayilar aynen, gruplamasiz ve ussuz kalmali. Formul sonucu da onbellek degeri de
                    // getNumericCellValue() ile gelir (evaluateFormulaCell sonucu hucreye yazar).
                    text = plainNumber(cell.getNumericCellValue());
                } else if (!cachedFallback) {
                    text = formatter.formatCellValue(cell, evaluator);
                } else if (type == CellType.STRING) {
                    text = cell.getStringCellValue();
                } else if (type == CellType.BOOLEAN) {
                    text = String.valueOf(cell.getBooleanCellValue());
                } else {
                    text = null;
                }
            } catch (RuntimeException e) {
                text = null;
            }
        }
        text = text == null ? "" : text.strip();
        return text.isEmpty() ? null : text;
    }

    /** Tam sayilar gruplamasiz/ussuz ("900164000123456"); kesirliler '.' ayiracli ("12.5"). */
    static String plainNumber(double v) {
        BigDecimal d = BigDecimal.valueOf(v);
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return d.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
        }
        return d.stripTrailingZeros().toPlainString();
    }

    static String normalizeHeader(String header) {
        String s = TurkishText.fold(header.strip());
        StringBuilder sb = new StringBuilder();
        boolean pendingSep = false;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-') {
                pendingSep = true;
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                if (pendingSep && sb.length() > 0) {
                    sb.append('_');
                }
                pendingSep = false;
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
