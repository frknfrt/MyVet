package com.vetos.modules.notification.application.dto;

import com.vetos.modules.notification.domain.NotificationType;

import java.util.List;

public record NotificationTemplateSummary(
    NotificationType notificationType,
    String templateText,
    boolean customized,
    List<String> placeholders
) {
}
