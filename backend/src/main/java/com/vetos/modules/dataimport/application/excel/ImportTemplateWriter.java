package com.vetos.modules.dataimport.application.excel;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Bos aktarim sablonunu (Sahipler + Hastalar, kalin baslik, 2. satirda ornek kayit) yazar. */
@org.springframework.stereotype.Component
public class ImportTemplateWriter {

    public void write(OutputStream out) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);

            writeSheet(wb, header, ImportTemplateColumns.OWNERS_SHEET, ImportTemplateColumns.OWNER_COLUMNS,
                    ImportTemplateColumns.OWNER_REQUIRED,
                    List.of("1001", "Ayşe Yılmaz", "5321234567", "", "ayse@example.com", "", "Atatürk Cad. No:1",
                            "Ankara", "Çankaya", "Örnek kayıt, silebilirsiniz"));
            writeSheet(wb, header, ImportTemplateColumns.PATIENTS_SHEET, ImportTemplateColumns.PATIENT_COLUMNS,
                    ImportTemplateColumns.PATIENT_REQUIRED,
                    List.of("2001", "1001", "Pamuk", "Kedi", "Van", "Dişi", "2019-03-05", "Evet", "", "", "Beyaz",
                            "Örnek kayıt, silebilirsiniz"));
            wb.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeSheet(XSSFWorkbook wb, CellStyle header, String name, List<String> columns,
            List<String> required, List<String> example) {
        Sheet sheet = wb.createSheet(name);
        Row h = sheet.createRow(0);
        Row ex = sheet.createRow(1);
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            var c = h.createCell(i);
            c.setCellValue(required.contains(col) ? col + "*" : col);
            c.setCellStyle(header);
            if (!example.get(i).isEmpty()) {
                ex.createCell(i).setCellValue(example.get(i));
            }
            sheet.setColumnWidth(i, 20 * 256);
        }
    }
}
