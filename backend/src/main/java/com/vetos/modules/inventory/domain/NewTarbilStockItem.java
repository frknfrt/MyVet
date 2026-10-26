package com.vetos.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NewTarbilStockItem(
    String tarbilSystem, String productName, String presentation, String category,
    String lotNumber, LocalDate expiryDate, int quantity,
    /** Acik/parca miktar (null = bilinmiyor, 0 sayilir). */ BigDecimal openedQuantity,
    /** Asi kutusundaki flakon (TARBIL "Uretilen Doz-Kucuk Hayvan"); yoksa null. */ Integer dosesPerPackage,
    /** Acik urun son kullanma tarihi (spec 2026-10-25); yoksa null. */ LocalDate openedExpiryDate
) {
    public NewTarbilStockItem(String tarbilSystem, String productName, String presentation, String category, String lotNumber,
                              LocalDate expiryDate, int quantity, BigDecimal openedQuantity, Integer dosesPerPackage) {
        this(tarbilSystem, productName, presentation, category, lotNumber, expiryDate, quantity, openedQuantity, dosesPerPackage, null);
    }
}
