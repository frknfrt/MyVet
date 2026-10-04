package com.vetos.modules.integration.tarbil.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockSnapshotLineInput(String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                     int quantity, BigDecimal openedQuantity) {}
