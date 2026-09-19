package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryDueNotificationsUseCase {
    private static final int BATCH_SIZE = 50;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSendExecutor notificationSendExecutor;

    @Transactional
    public int execute() {
        List<NotificationLog> claimed = notificationLogRepository.claimDueForRetry(Instant.now(), BATCH_SIZE);
        for (NotificationLog log : claimed) {
            log.markRetrying();
            notificationLogRepository.save(log);
        }
        List<UUID> ids = claimed.stream().map(NotificationLog::getId).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ids.forEach(notificationSendExecutor::attemptSend);
                }
            });
        }
        return claimed.size();
    }
}
