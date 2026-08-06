package com.vetos.modules.notification.application.dto;

import java.time.Instant;

public record NotificationStatusSummary(
    long pendingCount, long sentCount, long failedCount, Instant lastSentAt, boolean connected
) {}
