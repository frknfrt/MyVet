package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.CreateMessageTemplateCommand;
import com.vetos.modules.notification.domain.MessageTemplate;
import com.vetos.modules.notification.domain.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateMessageTemplateUseCase {

    private final MessageTemplateRepository messageTemplateRepository;

    @Transactional
    public UUID execute(CreateMessageTemplateCommand command) {
        MessageTemplate template = MessageTemplate.create(
            command.tenantId(), command.name(), command.channel(), command.category(), command.body()
        );
        return messageTemplateRepository.save(template).getId();
    }
}
