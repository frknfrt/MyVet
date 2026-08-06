package com.vetos.modules.notification.infrastructure.persistence;

import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface NotificationLogJpaRepository extends JpaRepository<NotificationLog, UUID> {
    List<NotificationLog> findByTenantId(UUID tenantId);
    boolean existsByRelatedEntityIdAndNotificationType(UUID relatedEntityId, NotificationType notificationType);
}
