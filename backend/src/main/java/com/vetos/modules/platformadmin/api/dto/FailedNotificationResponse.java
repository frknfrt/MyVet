package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.notification.domain.FailedNotificationView;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record FailedNotificationResponse(
    UUID tenantId, String tenantName, String recipientLabel, String recipientContact,
    NotificationChannel channel, NotificationType notificationType, String failureReason,
    int attemptCount, Instant attemptedAt, Instant nextRetryAt
) {
    public static FailedNotificationResponse from(FailedNotificationView v) {
        return new FailedNotificationResponse(
            v.tenantId(), v.tenantName(), v.recipientLabel(), v.recipientContact(),
            v.channel(), v.notificationType(), v.failureReason(), v.attemptCount(), v.attemptedAt(), v.nextRetryAt()
        );
    }
}
