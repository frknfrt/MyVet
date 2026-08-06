package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueEInvoiceSubmissionUseCase {

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final EInvoiceSubmissionExecutor eInvoiceSubmissionExecutor;

    @Transactional
    public UUID execute(
        UUID tenantId, UUID invoiceId, UUID ownerId, EInvoiceDocumentType documentType, BigDecimal totalAmount, BigDecimal taxAmount
    ) {
        EInvoiceSubmission submission = eInvoiceSubmissionRepository.save(
            EInvoiceSubmission.queue(tenantId, invoiceId, ownerId, documentType, totalAmount, taxAmount)
        );
        UUID submissionId = submission.getId();

        // Cagiran kod (InvoiceIssuedEvent listener'i) bir ust @Transactional
        // icinde calisiyor -- gonderim denemesi, bu satir henuz commit
        // olmamis kaydi goremez diye transaction commit'ten SONRAYA
        // erteleniyor (QueueTarbilSyncUseCase ile ayni desen).
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

        return submissionId;
    }
}
