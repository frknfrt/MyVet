package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.*;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.platform.observability.LogMasking;
import com.vetos.platform.resilience.BackoffSchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final TenantLookupPort tenantLookupPort;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    // attemptCount 1..4 -> otomatik retry planlanir; 5. denemede (attemptCount==5) nextRetryAt=null,
    // yani en fazla 4 otomatik retry (toplam 5 deneme), sonrasinda sadece manuel "Tekrar Dene".
    // Takvim BackoffSchedule.OUTBOX'ta (e-fatura ile ortak).
    Instant computeNextRetryAt(int attemptCountAfterThisFailure) {
        return BackoffSchedule.OUTBOX.nextRetryAt(attemptCountAfterThisFailure, Instant.now());
    }

    @Async
    public void attemptSend(UUID logId) {
        NotificationSendRequest request = transactions.execute(tx -> prepare(logId));
        if (request == null) return;
        NotificationSendOutcome outcome = notificationSendPort.send(request);
        transactions.executeWithoutResult(tx -> finish(logId, outcome));
    }

    private NotificationSendRequest prepare(UUID logId) {
        var notificationLog = notificationLogRepository.findForUpdate(logId).orElse(null);
        if (notificationLog == null || notificationLog.getStatus() != NotificationStatus.PENDING) return null;
        notificationLog.beginDispatch();
        String outboundMessage = notificationLog.getMessage();
        if (notificationLog.getChannel() == NotificationChannel.WHATSAPP) {
            // WhatsApp'ta gercek gonderim onayli bir Message Template uzerinden yapilir
            // (bkz. NotificationProviderAdapter) ve sablonun tek degiskeni butun mesaji
            // tasir. Musterinin hangi klinikten yazildigini gorebilmesi icin klinigin
            // adini gonderilecek metnin basina ekliyoruz. NotificationLog'daki mesaj
            // (klinigin duzenledigi sablon metni) burada degistirilmez, sadece iletim
            // sirasinda kullanilan gecici metne eklenir.
            String tenantName = tenantLookupPort.findTenantName(notificationLog.getTenantId()).orElse("Vetly");
            outboundMessage = tenantName + ": " + outboundMessage;
        }

        return new NotificationSendRequest(notificationLog.getChannel(), notificationLog.getRecipientContact(), outboundMessage);
    }

    private void finish(UUID logId, NotificationSendOutcome outcome) {
        var notificationLog = notificationLogRepository.findForUpdate(logId).orElseThrow();
        if (notificationLog.getStatus() != NotificationStatus.DISPATCHING) return;
        if (outcome.uncertain()) {
            log.warn("Bildirim sonucu doğrulanmalı: logId={}", logId);
            return;
        }
        if (outcome.success()) {
            notificationLog.markSent();
        } else {
            notificationLog.markFailed(outcome.message(), computeNextRetryAt(notificationLog.getAttemptCount() + 1));
            log.warn("Bildirim gonderimi basarisiz: logId={}, sebep={}", logId, LogMasking.body(outcome.message()));
        }
        notificationLogRepository.save(notificationLog);
    }
}
