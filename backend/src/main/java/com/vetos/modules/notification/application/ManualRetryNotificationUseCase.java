package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationStatus;
import com.vetos.modules.notification.domain.exception.NotificationLogNotFoundException;
import com.vetos.modules.notification.domain.exception.NotificationLogNotRetryableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Otomatik yeniden deneme haklari tukenmis (bkz. NotificationSendExecutor --
 * en fazla 4 otomatik retry, 5. denemeden sonra nextRetryAt=null) bir
 * bildirimi platform admin'in elle tekrar denemesini saglar. Mantik,
 * RetryDueNotificationsUseCase'in tek kayit icin calisan esdegeridir.
 */
@Service
@RequiredArgsConstructor
public class ManualRetryNotificationUseCase {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSendExecutor notificationSendExecutor;

    @Transactional
    public void execute(UUID notificationLogId) {
        NotificationLog notificationLog = notificationLogRepository.findById(notificationLogId)
            .orElseThrow(() -> new NotificationLogNotFoundException(notificationLogId));

        if (notificationLog.getStatus() != NotificationStatus.FAILED) {
            throw new NotificationLogNotRetryableException(notificationLogId, notificationLog.getStatus());
        }

        notificationLog.markRetrying();
        notificationLogRepository.save(notificationLog);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationSendExecutor.attemptSend(notificationLogId);
                }
            });
        } else {
            notificationSendExecutor.attemptSend(notificationLogId);
        }
    }
}
