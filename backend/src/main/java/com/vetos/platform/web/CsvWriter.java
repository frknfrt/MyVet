package com.vetos.platform.web;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Basit, bagimliliksiz CSV uretici (@docs/requirements.md 4.12 "disa
 * aktarilabilir (Excel/PDF) rapor tablolari" -- Excel CSV'yi native acar,
 * ek bir kutuphaneye (Apache POI vb.) ihtiyac yok).
 */
public final class CsvWriter {

    private static final String UTF8_BOM = "﻿";

    private CsvWriter() {}

    public static <T> String toCsv(List<String> headers, List<T> rows, List<Function<T, String>> columnExtractors) {
        StringBuilder sb = new StringBuilder(UTF8_BOM);
        sb.append(headers.stream().map(CsvWriter::escape).collect(Collectors.joining(","))).append("\r\n");
        for (T row : rows) {
            String line = columnExtractors.stream().map(f -> escape(f.apply(row))).collect(Collectors.joining(","));
            sb.append(line).append("\r\n");
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
