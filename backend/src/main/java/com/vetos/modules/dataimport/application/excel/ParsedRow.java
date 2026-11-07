package com.vetos.modules.dataimport.application.excel;

import java.util.Map;

/**
 * Excel'den okunan tek satir. {@code rowNumber} 1 tabanli Excel satir numarasidir (baslik 1, ilk veri 2).
 * {@code values} sablon sutunlarini (normalize adla), {@code extras} sablonda olmayan sutunlari (orijinal baslikla) tasir.
 */
public record ParsedRow(int rowNumber, Map<String, String> values, Map<String, String> extras) {}
