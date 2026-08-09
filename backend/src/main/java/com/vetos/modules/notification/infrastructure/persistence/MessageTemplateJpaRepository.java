package com.vetos.modules.notification.infrastructure.persistence;

import com.vetos.modules.notification.domain.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface MessageTemplateJpaRepository extends JpaRepository<MessageTemplate, UUID> {
    List<MessageTemplate> findByTenantId(UUID tenantId);
}
