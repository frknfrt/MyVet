package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.NotificationSettingsSummary;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetNotificationSettingsUseCase {

    private final NotificationSettingsRepository notificationSettingsRepository;

    @Transactional(readOnly = true)
    public NotificationSettingsSummary execute(UUID tenantId) {
        NotificationChannel appointmentChannel = notificationSettingsRepository.findByTenantId(tenantId)
            .map(s -> s.getAppointmentChannel())
            .orElse(NotificationChannel.SMS);
        return new NotificationSettingsSummary(appointmentChannel);
    }
}
