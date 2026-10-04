package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.StockSyncLineView;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockSyncLineResponse(UUID lineId, String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                    int tarbilQuantity, BigDecimal openedQuantity, TarbilStockSyncStatus status,
                                    UUID inventoryItemId, Integer vetlyQuantity) {
    public static StockSyncLineResponse from(StockSyncLineView v) {
        return new StockSyncLineResponse(v.lineId(), v.productName(), v.presentation(), v.lotNumber(), v.expiryDate(),
            v.tarbilQuantity(), v.openedQuantity(), v.status(), v.inventoryItemId(), v.vetlyQuantity());
    }
}
