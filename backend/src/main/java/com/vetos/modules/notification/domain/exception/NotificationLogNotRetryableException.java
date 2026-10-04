package com.vetos.modules.notification.domain.exception;

import com.vetos.modules.notification.domain.NotificationStatus;
import com.vetos.platform.exception.DomainException;
import java.util.UUID;

/**
 * Sadece FAILED durumundaki bir bildirim manuel olarak tekrar denenebilir --
 * SENT olani tekrar gondermek mukerrer mesaja yol acar, PENDING olani ise
 * zaten bir deneme/otomatik retry bekliyordur.
 */
public class NotificationLogNotRetryableException extends DomainException {
    public NotificationLogNotRetryableException(UUID id, NotificationStatus currentStatus) {
        super(
            "NOTIFICATION_LOG_NOT_RETRYABLE",
            "Bu bildirim su an tekrar denenemez (durum: " + currentStatus + "): " + id
        );
    }
}
