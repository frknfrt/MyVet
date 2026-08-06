package com.vetos.modules.notification.domain;

public record NotificationSendRequest(NotificationChannel channel, String recipientContact, String message) {}
