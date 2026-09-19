package com.vetos.modules.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository {
    NotificationLog save(NotificationLog log);
    Optional<NotificationLog> findById(UUID id);
    List<NotificationLog> findByTenantId(UUID tenantId);
    boolean existsByRelatedEntityIdAndNotificationType(UUID relatedEntityId, NotificationType notificationType);
    List<NotificationLog> claimDueForRetry(Instant now, int limit);
}
