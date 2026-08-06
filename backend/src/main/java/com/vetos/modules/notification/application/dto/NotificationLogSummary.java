package com.vetos.modules.notification.application.dto;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationStatus;
import com.vetos.modules.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationLogSummary(
    UUID id, String ownerName, String recipientContact, NotificationChannel channel,
    NotificationType notificationType, String message, NotificationStatus status, Instant attemptedAt
) {}
