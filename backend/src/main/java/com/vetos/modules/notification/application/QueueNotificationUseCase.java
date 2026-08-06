package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueNotificationUseCase {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSendExecutor notificationSendExecutor;

    @Transactional
    public UUID execute(
        UUID tenantId, UUID ownerId, UUID patientId, NotificationChannel channel, NotificationType notificationType,
        String recipientContact, String message, UUID relatedEntityId
    ) {
        NotificationLog log = notificationLogRepository.save(
            NotificationLog.queue(tenantId, ownerId, patientId, channel, notificationType, recipientContact, message, relatedEntityId)
        );
        UUID logId = log.getId();

        // Cagiran kod (appointment event listener'lari, zamanlanmis hatirlatma
        // isi) genelde bir ust @Transactional icinde calisiyor -- gonderim
        // denemesi, bu satir henuz commit olmamis kaydi goremez diye
        // transaction commit'ten SONRAYA erteleniyor (QueueTarbilSyncUseCase
        // ile ayni desen).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationSendExecutor.attemptSend(logId);
                }
            });
        } else {
            notificationSendExecutor.attemptSend(logId);
        }

        return logId;
    }
}
