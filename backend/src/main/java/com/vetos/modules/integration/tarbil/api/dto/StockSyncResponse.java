package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.StockSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockSyncResponse(UUID snapshotId, TarbilStockSystem system, Instant takenAt, List<StockSyncLineResponse> lines) {
    public static StockSyncResponse from(StockSyncView v) {
        return new StockSyncResponse(v.snapshotId(), v.system(), v.takenAt(), v.lines().stream().map(StockSyncLineResponse::from).toList());
    }
}
