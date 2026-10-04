package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** snapshotId null ise bu sistem icin henuz goruntu yok. */
public record StockSyncView(UUID snapshotId, TarbilStockSystem system, Instant takenAt, List<StockSyncLineView> lines) {}
