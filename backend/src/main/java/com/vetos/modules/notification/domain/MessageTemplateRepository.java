package com.vetos.modules.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageTemplateRepository {
    MessageTemplate save(MessageTemplate template);
    Optional<MessageTemplate> findById(UUID id);
    List<MessageTemplate> findByTenantId(UUID tenantId);
    void deleteById(UUID id);
}
