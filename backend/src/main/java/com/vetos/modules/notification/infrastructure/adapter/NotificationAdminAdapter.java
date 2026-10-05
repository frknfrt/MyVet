package com.vetos.modules.notification.infrastructure.adapter;

import com.vetos.modules.notification.application.ManualRetryNotificationUseCase;
import com.vetos.modules.notification.domain.NotificationAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class NotificationAdminAdapter implements NotificationAdminPort {

    private final ManualRetryNotificationUseCase manualRetryNotificationUseCase;

    @Override
    public void retryNow(UUID notificationLogId) {
        manualRetryNotificationUseCase.execute(notificationLogId);
    }
}
