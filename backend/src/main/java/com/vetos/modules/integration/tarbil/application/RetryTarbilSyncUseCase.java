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

    @Transactional
    public void execute(UUID logId) {
        TarbilSyncLog log = tarbilSyncLogRepository.findById(logId)
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
