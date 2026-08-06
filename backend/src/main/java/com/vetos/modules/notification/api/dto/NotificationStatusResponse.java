package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.application.dto.NotificationStatusSummary;

import java.time.Instant;

public record NotificationStatusResponse(long pendingCount, long sentCount, long failedCount, Instant lastSentAt, boolean connected) {
    public static NotificationStatusResponse from(NotificationStatusSummary s) {
        return new NotificationStatusResponse(s.pendingCount(), s.sentCount(), s.failedCount(), s.lastSentAt(), s.connected());
    }
}
