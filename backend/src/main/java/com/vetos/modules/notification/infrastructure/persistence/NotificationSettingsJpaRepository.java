package com.vetos.modules.notification.infrastructure.persistence;

import com.vetos.modules.notification.domain.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface NotificationSettingsJpaRepository extends JpaRepository<NotificationSettings, UUID> {
}
