package com.vetos.modules.integration.tarbil.application.dto;

import java.time.Instant;

public record TarbilStatusSummary(
    long pendingCount, long syncedCount, long failedCount, Instant lastSyncedAt, boolean connected
) {}
