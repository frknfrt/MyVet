package com.vetos.modules.notification.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class MessageTemplateNotFoundException extends DomainException {
    public MessageTemplateNotFoundException(UUID id) {
        super("MESSAGE_TEMPLATE_NOT_FOUND", "Mesaj sablonu bulunamadi: " + id);
    }
}
