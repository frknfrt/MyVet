package com.vetos.modules.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform admin Sistem Sagligi paneli icin -- basarisiz bir bildirim
 * kaydinin kiracI adiyla birlikte ozeti. bkz. NotificationHealthPort.
 */
public record FailedNotificationView(
    UUID tenantId, String tenantName, String recipientLabel, String recipientContact,
    NotificationChannel channel, NotificationType notificationType, String failureReason,
    int attemptCount, Instant attemptedAt, Instant nextRetryAt
) {}
