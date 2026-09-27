package com.vetos.modules.notification.infrastructure.persistence;

import com.vetos.modules.notification.domain.NotificationSettings;
import com.vetos.modules.notification.domain.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class NotificationSettingsRepositoryAdapter implements NotificationSettingsRepository {

    private final NotificationSettingsJpaRepository jpaRepository;

    @Override
    public Optional<NotificationSettings> findByTenantId(UUID tenantId) { return jpaRepository.findById(tenantId); }

    @Override
    public NotificationSettings save(NotificationSettings settings) { return jpaRepository.save(settings); }
}
