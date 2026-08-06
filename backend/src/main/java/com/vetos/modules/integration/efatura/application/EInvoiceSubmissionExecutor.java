package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.billing.domain.InvoiceEInvoiceUpdatePort;
import com.vetos.modules.integration.efatura.domain.*;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Gercek bir mesaj kuyrugu (RabbitMQ/Kafka) Faz 1'de kurulu degil; ayni
 * "asenkron, cagirani bloklamayan, tekrar denenebilir" davranisi Spring
 * @Async + EInvoiceSubmission outbox kaydiyla sagliyoruz (TarbilSyncExecutor
 * ile ayni desen). Basarili gonderimde GIB referansi ilgili faturaya
 * (InvoiceEInvoiceUpdatePort uzerinden) geri yazilir.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class EInvoiceSubmissionExecutor {

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final EInvoiceGatewayPort eInvoiceGatewayPort;
    private final OwnerLookupPort ownerLookupPort;
    private final InvoiceEInvoiceUpdatePort invoiceEInvoiceUpdatePort;

    @Async
    @Transactional
    public void attemptSubmit(UUID submissionId) {
        EInvoiceSubmission submission = eInvoiceSubmissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            return;
        }

        var owner = ownerLookupPort.findSummaryById(submission.getOwnerId());
        EInvoiceSubmissionOutcome outcome = eInvoiceGatewayPort.submit(new EInvoiceSubmissionRequest(
            submission.getInvoiceId(), submission.getDocumentType(), owner.fullName(), owner.address(),
            owner.nationalIdMasked(), submission.getTotalAmount(), submission.getTaxAmount()
        ));

        if (outcome.success()) {
            submission.markSubmitted(outcome.gibReference());
            invoiceEInvoiceUpdatePort.applyEInvoiceReference(submission.getInvoiceId(), outcome.gibReference());
        } else {
            submission.markFailed();
            log.warn("e-Fatura gonderimi basarisiz: submissionId={}, sebep={}", submissionId, outcome.message());
        }
        eInvoiceSubmissionRepository.save(submission);
    }
}
