package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.TarbilStatusSummary;

import java.time.Instant;

public record TarbilStatusResponse(long pendingCount, long submittedCount, long dismissedCount, Instant lastSubmittedAt) {
    public static TarbilStatusResponse from(TarbilStatusSummary s) {
        return new TarbilStatusResponse(s.pendingCount(), s.submittedCount(), s.dismissedCount(), s.lastSubmittedAt());
    }
}
