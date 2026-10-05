package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.integration.tarbil.domain.FailedTarbilSyncView;

import java.time.Instant;
import java.util.UUID;

public record FailedTarbilSyncResponse(
    UUID syncLogId, UUID tenantId, String tenantName, UUID patientId, String patientName,
    String syncType, String failureReason, int attemptCount, Instant attemptedAt
) {
    public static FailedTarbilSyncResponse from(FailedTarbilSyncView v) {
        return new FailedTarbilSyncResponse(
            v.syncLogId(), v.tenantId(), v.tenantName(), v.patientId(), v.patientName(),
            v.syncType(), v.failureReason(), v.attemptCount(), v.attemptedAt()
        );
    }
}
