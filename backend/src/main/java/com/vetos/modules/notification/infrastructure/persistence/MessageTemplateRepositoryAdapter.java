package com.vetos.modules.notification.infrastructure.persistence;

import com.vetos.modules.notification.domain.MessageTemplate;
import com.vetos.modules.notification.domain.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class MessageTemplateRepositoryAdapter implements MessageTemplateRepository {

    private final MessageTemplateJpaRepository jpaRepository;

    @Override
    public MessageTemplate save(MessageTemplate template) { return jpaRepository.save(template); }

    @Override
    public Optional<MessageTemplate> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<MessageTemplate> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public void deleteById(UUID id) { jpaRepository.deleteById(id); }
}
