package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.MessageTemplateSummary;
import com.vetos.modules.notification.domain.MessageTemplate;
import com.vetos.modules.notification.domain.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListMessageTemplatesUseCase {

    private final MessageTemplateRepository messageTemplateRepository;

    @Transactional(readOnly = true)
    public List<MessageTemplateSummary> execute(UUID tenantId) {
        return messageTemplateRepository.findByTenantId(tenantId).stream()
            .map(this::toSummary)
            .sorted(Comparator.comparing(MessageTemplateSummary::name))
            .toList();
    }

    private MessageTemplateSummary toSummary(MessageTemplate t) {
        return new MessageTemplateSummary(t.getId(), t.getName(), t.getChannel(), t.getCategory(), t.getBody(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
