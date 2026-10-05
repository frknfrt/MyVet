package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.application.dto.NotificationTemplateSummary;
import com.vetos.modules.notification.domain.NotificationType;

import java.util.List;

public record NotificationTemplateResponse(
    NotificationType notificationType,
    String templateText,
    boolean customized,
    List<String> placeholders
) {
    public static NotificationTemplateResponse from(NotificationTemplateSummary summary) {
        return new NotificationTemplateResponse(
            summary.notificationType(), summary.templateText(), summary.customized(), summary.placeholders()
        );
    }
}
