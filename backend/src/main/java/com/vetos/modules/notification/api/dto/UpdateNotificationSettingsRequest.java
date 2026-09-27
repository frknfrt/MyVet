package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.domain.NotificationChannel;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingsRequest(@NotNull NotificationChannel appointmentChannel) {
}
