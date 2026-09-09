package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import com.vetos.modules.integration.efatura.domain.exception.EInvoiceSubmissionAlreadyProcessingException;
import com.vetos.modules.integration.efatura.domain.exception.EInvoiceSubmissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryEInvoiceSubmissionUseCase {

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final EInvoiceSubmissionExecutor eInvoiceSubmissionExecutor;

    @Transactional
    public void execute(UUID submissionId) {
        EInvoiceSubmission submission = eInvoiceSubmissionRepository.findById(submissionId)
            .orElseThrow(() -> new EInvoiceSubmissionNotFoundException(submissionId));

        // PROCESSING: saglayici istegi zaten kabul etti, GIB resmilesme
        // callback'i bekleniyor -- tekrar gonderim mukerrer fatura yaratir.
        if (submission.getStatus() == EInvoiceSubmissionStatus.PROCESSING) {
            throw new EInvoiceSubmissionAlreadyProcessingException(submissionId);
        }

        submission.markRetrying();
        eInvoiceSubmissionRepository.save(submission);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eInvoiceSubmissionExecutor.attemptSubmit(submissionId);
                }
            });
        } else {
            eInvoiceSubmissionExecutor.attemptSubmit(submissionId);
        }
    }
}
