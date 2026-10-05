package com.vetos.modules.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationMessageTemplateRepository {

    Optional<NotificationMessageTemplate> findByTenantIdAndNotificationType(UUID tenantId, NotificationType notificationType);

    List<NotificationMessageTemplate> findAllByTenantId(UUID tenantId);

    NotificationMessageTemplate save(NotificationMessageTemplate template);

    void deleteByTenantIdAndNotificationType(UUID tenantId, NotificationType notificationType);
}
