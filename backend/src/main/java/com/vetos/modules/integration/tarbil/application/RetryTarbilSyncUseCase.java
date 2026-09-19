package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSyncLogNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryTarbilSyncUseCase {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;
    private final TarbilSyncExecutor tarbilSyncExecutor;

    /**
     * TarbilSyncLog @TenantId DISINDA tutuluyor (NotificationLog ile ayni
     * karar) -- bu yuzden kiraci kontrolu ELLE yapilir. Baska kiracinin
     * kaydi, mevcut TarbilSyncLogNotFoundException (404) ile "yok" gibi
     * gorunur; var oldugu bile sizdirilmaz.
     */
    @Transactional
    public void execute(UUID tenantId, UUID logId) {
        TarbilSyncLog log = tarbilSyncLogRepository.findById(logId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSyncLogNotFoundException(logId));
        log.markRetrying();
        tarbilSyncLogRepository.save(log);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tarbilSyncExecutor.attemptSync(logId);
                }
            });
        } else {
            tarbilSyncExecutor.attemptSync(logId);
        }
    }
}
