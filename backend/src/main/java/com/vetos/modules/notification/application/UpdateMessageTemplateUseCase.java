package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.UpdateMessageTemplateCommand;
import com.vetos.modules.notification.domain.MessageTemplateRepository;
import com.vetos.modules.notification.domain.exception.MessageTemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateMessageTemplateUseCase {

    private final MessageTemplateRepository messageTemplateRepository;

    @Transactional
    public void execute(UpdateMessageTemplateCommand command) {
        var template = messageTemplateRepository.findById(command.templateId())
            .orElseThrow(() -> new MessageTemplateNotFoundException(command.templateId()));
        template.update(command.name(), command.channel(), command.category(), command.body());
        messageTemplateRepository.save(template);
    }
}
