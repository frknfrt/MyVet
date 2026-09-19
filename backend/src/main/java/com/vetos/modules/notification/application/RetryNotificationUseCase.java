package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.exception.NotificationLogNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryNotificationUseCase {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSendExecutor notificationSendExecutor;

    /**
     * NotificationLog @TenantId DISINDA tutuluyor (tasarim dokumani S6) --
     * bu yuzden kiraci kontrolu ELLE yapilir. Baska kiracinin kaydi,
     * mevcut NotificationLogNotFoundException (404) ile "yok" gibi gorunur;
     * var oldugu bile sizdirilmaz.
     */
    @Transactional
    public void execute(UUID tenantId, UUID logId) {
        NotificationLog log = notificationLogRepository.findById(logId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new NotificationLogNotFoundException(logId));
        log.markRetrying();
        notificationLogRepository.save(log);

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
    }
}
