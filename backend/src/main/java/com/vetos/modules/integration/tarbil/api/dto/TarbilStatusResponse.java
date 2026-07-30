package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.TarbilStatusSummary;

import java.time.Instant;

public record TarbilStatusResponse(long pendingCount, long syncedCount, long failedCount, Instant lastSyncedAt, boolean connected) {
    public static TarbilStatusResponse from(TarbilStatusSummary s) {
        return new TarbilStatusResponse(s.pendingCount(), s.syncedCount(), s.failedCount(), s.lastSyncedAt(), s.connected());
    }
}
