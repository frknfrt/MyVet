package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.StockSyncLineView;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StockSyncLineResponse(UUID lineId, String productName, String presentation, String lotNumber, LocalDate expiryDate,
                                    /** Spec 2026-10-24: urun+seri basina tek satir; tarbilQuantity kapali adet, tarbilOpenedQuantity acik/parca (null = bilinmiyor). */
                                    int tarbilQuantity, BigDecimal tarbilOpenedQuantity, TarbilStockSyncStatus status,
                                    UUID inventoryItemId, Integer vetlyQuantity, BigDecimal vetlyOpenedQuantity, String unit,
                                    boolean partStock, int pendingTarbilCount) {
    public static StockSyncLineResponse from(StockSyncLineView v) {
        return new StockSyncLineResponse(v.lineId(), v.productName(), v.presentation(), v.lotNumber(), v.expiryDate(),
            v.tarbilQuantity(), v.tarbilOpenedQuantity(), v.status(), v.inventoryItemId(), v.vetlyQuantity(), v.vetlyOpenedQuantity(),
            v.unit(), v.partStock(), v.pendingTarbilCount());
    }
}
