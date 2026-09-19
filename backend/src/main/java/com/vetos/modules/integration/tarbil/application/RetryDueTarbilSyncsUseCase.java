package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
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
public class RetryDueTarbilSyncsUseCase {
    private static final int BATCH_SIZE = 50;
    private final TarbilSyncLogRepository tarbilSyncLogRepository;
    private final TarbilSyncExecutor tarbilSyncExecutor;

    @Transactional
    public int execute() {
        List<TarbilSyncLog> claimed = tarbilSyncLogRepository.claimDueForRetry(Instant.now(), BATCH_SIZE);
        for (TarbilSyncLog log : claimed) {
            log.markRetrying();
            tarbilSyncLogRepository.save(log);
        }
        List<UUID> ids = claimed.stream().map(TarbilSyncLog::getId).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ids.forEach(tarbilSyncExecutor::attemptSync);
                }
            });
        }
        return claimed.size();
    }
}
