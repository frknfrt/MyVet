package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockSnapshotLineRequest(
    @NotBlank @Size(max = 300) String productName,
    @Size(max = 200) String presentation,
    @Size(max = 100) String lotNumber,
    LocalDate expiryDate,
    @Min(0) int quantity,
    BigDecimal openedQuantity,
    /** Asi sayfasindaki "Parca Stok Ara" tablosundan (2026-10-21). Eski eklenti gondermez = false. */
    Boolean partStock,
    /** Asi butun stok "Uretilen Doz-Kucuk Hayvan" (spec 2026-10-24); eski eklenti gondermez. */
    @jakarta.validation.constraints.Min(1) Integer dosesPerPackage,
    /** Ilac stok "Acilmis Kalan Miktar Son Kullanma Tarihi" (spec 2026-10-25); eski eklenti gondermez. */
    LocalDate openedExpiryDate
) {
    public StockSnapshotLineRequest(String productName, String presentation, String lotNumber, LocalDate expiryDate, int quantity,
                                    BigDecimal openedQuantity, Boolean partStock, Integer dosesPerPackage) {
        this(productName, presentation, lotNumber, expiryDate, quantity, openedQuantity, partStock, dosesPerPackage, null);
    }
}
