package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteMessageTemplateUseCase {

    private final MessageTemplateRepository messageTemplateRepository;

    @Transactional
    public void execute(UUID templateId) {
        messageTemplateRepository.deleteById(templateId);
    }
}
