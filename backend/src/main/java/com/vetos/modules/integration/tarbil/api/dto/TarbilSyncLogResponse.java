package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSyncLogSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;

import java.time.Instant;
import java.util.UUID;

public record TarbilSyncLogResponse(
    UUID id, UUID patientId, String patientName, TarbilSyncType syncType, TarbilSyncStatus status, Instant attemptedAt
) {
    public static TarbilSyncLogResponse from(TarbilSyncLogSummary s) {
        return new TarbilSyncLogResponse(s.id(), s.patientId(), s.patientName(), s.syncType(), s.status(), s.attemptedAt());
    }
}
