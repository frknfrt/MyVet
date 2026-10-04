package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockSyncLineView(UUID lineId, String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                int tarbilQuantity, BigDecimal openedQuantity, TarbilStockSyncStatus status,
                                UUID inventoryItemId, Integer vetlyQuantity) {}
