package com.vetos.modules.notification.infrastructure.persistence;

import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class NotificationLogRepositoryAdapter implements NotificationLogRepository {

    private final NotificationLogJpaRepository jpaRepository;

    @Override
    public NotificationLog save(NotificationLog log) { return jpaRepository.save(log); }

    @Override
    public Optional<NotificationLog> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<NotificationLog> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public boolean existsByRelatedEntityIdAndNotificationType(UUID relatedEntityId, NotificationType notificationType) {
        return jpaRepository.existsByRelatedEntityIdAndNotificationType(relatedEntityId, notificationType);
    }
}
