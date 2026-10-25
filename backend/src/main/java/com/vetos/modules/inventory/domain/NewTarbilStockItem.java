package com.vetos.modules.inventory.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NewTarbilStockItem(
    String tarbilSystem, String productName, String presentation, String category,
    String lotNumber, LocalDate expiryDate, int quantity,
    /** Acik/parca miktar (null = bilinmiyor, 0 sayilir). */ BigDecimal openedQuantity,
    /** Asi kutusundaki flakon (TARBIL "Uretilen Doz-Kucuk Hayvan"); yoksa null. */ Integer dosesPerPackage
) {}
