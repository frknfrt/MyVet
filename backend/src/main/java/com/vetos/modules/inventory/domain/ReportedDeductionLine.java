package com.vetos.modules.inventory.domain;

/** Eklentinin TARBIL'de onaylanmis olarak bildirdigi satir (spec 2026-10-09 S5.3). */
public record ReportedDeductionLine(String productName, String serialNumber, int quantity) {}
