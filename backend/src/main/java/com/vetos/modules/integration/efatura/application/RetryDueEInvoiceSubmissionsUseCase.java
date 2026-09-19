package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
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
public class RetryDueEInvoiceSubmissionsUseCase {
    private static final int BATCH_SIZE = 50;
    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final EInvoiceSubmissionExecutor eInvoiceSubmissionExecutor;

    @Transactional
    public int execute() {
        List<EInvoiceSubmission> claimed = eInvoiceSubmissionRepository.claimDueForRetry(Instant.now(), BATCH_SIZE);
        for (EInvoiceSubmission submission : claimed) {
            submission.markRetrying();
            eInvoiceSubmissionRepository.save(submission);
        }
        List<UUID> ids = claimed.stream().map(EInvoiceSubmission::getId).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ids.forEach(eInvoiceSubmissionExecutor::attemptSubmit);
                }
            });
        }
        return claimed.size();
    }
}
