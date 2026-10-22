package com.vetos.modules.integration.tarbil.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockSnapshotLineInput(String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                     int quantity, BigDecimal openedQuantity, boolean partStock) {

    public StockSnapshotLineInput(String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                  int quantity, BigDecimal openedQuantity) {
        this(productName, presentation, lotNumber, expiryDate, quantity, openedQuantity, false);
    }
}
