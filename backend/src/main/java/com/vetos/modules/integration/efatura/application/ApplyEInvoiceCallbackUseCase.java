package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.billing.domain.InvoiceEInvoiceUpdatePort;
import com.vetos.modules.integration.efatura.domain.EInvoiceGatewayPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionOutcome;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * faturaentegrator.com'un callback_url'ine gelen bildirimi isler --
 * FaturaEntegratorCallbackController tarafindan (hash imzasi dogrulandiktan
 * SONRA) cagrilir. Bildirim govdesi durum bilgisi ICERMEZ (sadece hangi
 * faturanin degistigini soyler) -- bu yuzden guncel durum ayrica
 * EInvoiceGatewayPort.fetchStatus() ile (GET /api/invoices/{id}) sorgulanir.
 *
 * Idempotent: ayni fatura icin birden fazla bildirim gelebilir (dokumantasyon
 * bunu acikca belirtiyor) -- zaten SUBMITTED/FAILED olan kayit icin hicbir
 * sey yapilmaz. Fatura hala isleniyorsa (outcome.finalResult()==false)
 * durum degismeden birakilir, bir sonraki bildirim beklenir.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApplyEInvoiceCallbackUseCase {

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final EInvoiceGatewayPort eInvoiceGatewayPort;
    private final InvoiceEInvoiceUpdatePort invoiceEInvoiceUpdatePort;

    @Transactional
    public void execute(String providerReference) {
        Optional<EInvoiceSubmission> maybeSubmission = eInvoiceSubmissionRepository.findByProviderReference(providerReference);
        if (maybeSubmission.isEmpty()) {
            log.warn("faturaentegrator callback bilinmeyen providerReference icin geldi: {}", providerReference);
            return;
        }

        EInvoiceSubmission submission = maybeSubmission.get();
        if (submission.getStatus() == EInvoiceSubmissionStatus.SUBMITTED || submission.getStatus() == EInvoiceSubmissionStatus.FAILED) {
            log.info("faturaentegrator callback tekrarlandi, kayit zaten sonuclanmis: providerReference={}", providerReference);
            return;
        }

        EInvoiceSubmissionOutcome outcome = eInvoiceGatewayPort.fetchStatus(providerReference);
        if (!outcome.success()) {
            submission.markFailed(outcome.message(), null);
            eInvoiceSubmissionRepository.save(submission);
            log.warn("faturaentegrator durumu basarisiz bildirdi: providerReference={}, sebep={}", providerReference, outcome.message());
        } else if (outcome.finalResult()) {
            submission.markSubmitted(outcome.gibReference());
            invoiceEInvoiceUpdatePort.applyEInvoiceReference(submission.getInvoiceId(), outcome.gibReference());
            eInvoiceSubmissionRepository.save(submission);
        } else {
            log.info("faturaentegrator callback geldi ama fatura hala isleniyor: providerReference={}", providerReference);
        }
    }
}
