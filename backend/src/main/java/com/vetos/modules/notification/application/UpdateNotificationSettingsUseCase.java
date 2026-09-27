package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationSettings;
import com.vetos.modules.notification.domain.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateNotificationSettingsUseCase {

    private final NotificationSettingsRepository notificationSettingsRepository;

    @Transactional
    public void execute(UUID tenantId, NotificationChannel appointmentChannel) {
        NotificationSettings settings = notificationSettingsRepository.findByTenantId(tenantId)
            .orElseGet(() -> NotificationSettings.defaultFor(tenantId));
        settings.updateAppointmentChannel(appointmentChannel);
        notificationSettingsRepository.save(settings);
    }
}
