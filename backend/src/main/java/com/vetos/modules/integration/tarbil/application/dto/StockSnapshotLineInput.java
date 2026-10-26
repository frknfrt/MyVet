package com.vetos.modules.integration.tarbil.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockSnapshotLineInput(String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                     int quantity, BigDecimal openedQuantity, boolean partStock, Integer dosesPerPackage,
                                     /** Acik urun son kullanma tarihi (spec 2026-10-25); yoksa null. */ LocalDate openedExpiryDate) {
    public StockSnapshotLineInput(String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                  int quantity, BigDecimal openedQuantity, boolean partStock, Integer dosesPerPackage) {
        this(productName, presentation, lotNumber, expiryDate, quantity, openedQuantity, partStock, dosesPerPackage, null);
    }

    public StockSnapshotLineInput(String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                  int quantity, BigDecimal openedQuantity, boolean partStock) {
        this(productName, presentation, lotNumber, expiryDate, quantity, openedQuantity, partStock, null, null);
    }


    public StockSnapshotLineInput(String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                  int quantity, BigDecimal openedQuantity) {
        this(productName, presentation, lotNumber, expiryDate, quantity, openedQuantity, false, null, null);
    }
}
