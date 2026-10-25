package com.vetos.modules.inventory.domain;

/** Eklentinin TARBIL'de onaylanmis olarak bildirdigi satir (spec 2026-10-09 S5.3). */
/** amount: TARBIL'de dusulen miktar (kullanim biriminde); source: hangi listeden (null = bilinmiyor, Vetly kurali). */
public record ReportedDeductionLine(String productName, String serialNumber, java.math.BigDecimal amount, StockSource source) {}
