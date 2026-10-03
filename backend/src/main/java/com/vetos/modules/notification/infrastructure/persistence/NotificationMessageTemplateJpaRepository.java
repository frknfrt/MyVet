package com.vetos.modules.notification.infrastructure.persistence;

import com.vetos.modules.notification.domain.NotificationMessageTemplate;
import com.vetos.modules.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface NotificationMessageTemplateJpaRepository extends JpaRepository<NotificationMessageTemplate, UUID> {

    Optional<NotificationMessageTemplate> findByTenantIdAndNotificationType(UUID tenantId, NotificationType notificationType);

    List<NotificationMessageTemplate> findAllByTenantId(UUID tenantId);

    void deleteByTenantIdAndNotificationType(UUID tenantId, NotificationType notificationType);
}
