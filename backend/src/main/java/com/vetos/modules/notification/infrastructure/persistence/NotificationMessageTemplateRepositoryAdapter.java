package com.vetos.modules.notification.infrastructure.persistence;

import com.vetos.modules.notification.domain.NotificationMessageTemplate;
import com.vetos.modules.notification.domain.NotificationMessageTemplateRepository;
import com.vetos.modules.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class NotificationMessageTemplateRepositoryAdapter implements NotificationMessageTemplateRepository {

    private final NotificationMessageTemplateJpaRepository jpaRepository;

    @Override
    public Optional<NotificationMessageTemplate> findByTenantIdAndNotificationType(UUID tenantId, NotificationType notificationType) {
        return jpaRepository.findByTenantIdAndNotificationType(tenantId, notificationType);
    }

    @Override
    public List<NotificationMessageTemplate> findAllByTenantId(UUID tenantId) {
        return jpaRepository.findAllByTenantId(tenantId);
    }

    @Override
    public NotificationMessageTemplate save(NotificationMessageTemplate template) {
        return jpaRepository.save(template);
    }

    @Override
    public void deleteByTenantIdAndNotificationType(UUID tenantId, NotificationType notificationType) {
        jpaRepository.deleteByTenantIdAndNotificationType(tenantId, notificationType);
    }
}
