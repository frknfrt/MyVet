package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Gercek bir mesaj kuyrugu (RabbitMQ/Kafka) Faz 1'de kurulu degil; ayni
 * "asenkron, cagirani bloklamayan, tekrar denenebilir" davranisi Spring
 * @Async + NotificationLog outbox kaydiyla sagliyoruz (TarbilSyncExecutor
 * ile ayni desen). Ileride gercek bir kuyruk eklenirse sadece bu sinifin
 * ici degisir, QueueNotificationUseCase'i cagiran kod etkilenmez.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class NotificationSendExecutor {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSendPort notificationSendPort;

    @Async
    @Transactional
    public void attemptSend(UUID logId) {
        NotificationLog notificationLog = notificationLogRepository.findById(logId).orElse(null);
        if (notificationLog == null) {
            return;
        }

        NotificationSendOutcome outcome = notificationSendPort.send(
            new NotificationSendRequest(notificationLog.getChannel(), notificationLog.getRecipientContact(), notificationLog.getMessage())
        );

        if (outcome.success()) {
            notificationLog.markSent();
        } else {
            notificationLog.markFailed();
            log.warn("Bildirim gonderimi basarisiz: logId={}, sebep={}", logId, outcome.message());
        }
        notificationLogRepository.save(notificationLog);
    }
}
