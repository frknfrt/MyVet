package com.vetos.modules.integration.efatura.api.dto;

import com.vetos.modules.integration.efatura.application.dto.EInvoiceStatusSummary;

import java.time.Instant;

public record EInvoiceStatusResponse(long pendingCount, long submittedCount, long failedCount, Instant lastSubmittedAt, boolean connected) {
    public static EInvoiceStatusResponse from(EInvoiceStatusSummary s) {
        return new EInvoiceStatusResponse(s.pendingCount(), s.submittedCount(), s.failedCount(), s.lastSubmittedAt(), s.connected());
    }
}
