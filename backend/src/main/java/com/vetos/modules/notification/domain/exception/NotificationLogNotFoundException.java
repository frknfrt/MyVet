package com.vetos.modules.notification.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class NotificationLogNotFoundException extends DomainException {
    public NotificationLogNotFoundException(UUID id) {
        super("NOTIFICATION_LOG_NOT_FOUND", "Bildirim kaydi bulunamadi: " + id);
    }
}
