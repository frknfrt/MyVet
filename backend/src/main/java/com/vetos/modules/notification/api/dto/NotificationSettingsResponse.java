package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.application.dto.NotificationSettingsSummary;
import com.vetos.modules.notification.domain.NotificationChannel;

public record NotificationSettingsResponse(NotificationChannel appointmentChannel) {
    public static NotificationSettingsResponse from(NotificationSettingsSummary s) {
        return new NotificationSettingsResponse(s.appointmentChannel());
    }
}
