package com.vetos.modules.integration.efatura.application.dto;

import java.time.Instant;

public record EInvoiceStatusSummary(
    long pendingCount, long submittedCount, long failedCount, Instant lastSubmittedAt, boolean connected
) {}
