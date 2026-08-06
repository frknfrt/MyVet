package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.application.dto.NotificationLogSummary;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationStatus;
import com.vetos.modules.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationLogResponse(
    UUID id, String ownerName, String recipientContact, NotificationChannel channel,
    NotificationType notificationType, String message, NotificationStatus status, Instant attemptedAt
) {
    public static NotificationLogResponse from(NotificationLogSummary s) {
        return new NotificationLogResponse(
            s.id(), s.ownerName(), s.recipientContact(), s.channel(), s.notificationType(), s.message(), s.status(), s.attemptedAt()
        );
    }
}
