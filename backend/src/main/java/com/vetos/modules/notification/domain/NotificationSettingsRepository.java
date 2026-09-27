package com.vetos.modules.notification.domain;

import java.util.Optional;
import java.util.UUID;

public interface NotificationSettingsRepository {
    Optional<NotificationSettings> findByTenantId(UUID tenantId);
    NotificationSettings save(NotificationSettings settings);
}
