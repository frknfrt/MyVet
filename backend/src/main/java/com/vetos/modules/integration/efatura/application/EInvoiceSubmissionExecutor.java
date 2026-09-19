package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.billing.domain.InvoiceEInvoiceUpdatePort;
import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.integration.efatura.domain.*;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.OwnerSummary;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gercek bir mesaj kuyrugu (RabbitMQ/Kafka) Faz 1'de kurulu degil; ayni
 * "asenkron, cagirani bloklamayan, tekrar denenebilir" davranisi Spring
 * @Async + EInvoiceSubmission outbox kaydiyla sagliyoruz (TarbilSyncExecutor
 * ile ayni desen).
 *
 * Saglayici SENKRON (mock: MockEInvoiceGatewayAdapter) donerse
 * (outcome.finalResult()==true) GIB referansi hemen faturaya yazilir.
 * ASENKRON saglayici (faturaentegrator: FaturaEntegratorEInvoiceGatewayAdapter)
 * icin outcome.finalResult()==false doner -- submission PROCESSING'e gecer,
 * nihai ETTN daha sonra callback ile ApplyEInvoiceCallbackUseCase tarafindan
 * yazilir.
 *
 * buyerIdentifier her zaman "11111111111" (GIB'in isimsiz/nihai tuketici
 * TCKN'si) -- sahiplerin gercek TCKN'si sistemde tutulmuyor (bkz.
 * EInvoiceSubmissionRequest javadoc).
 */
@Component
@Slf4j
@RequiredArgsConstructor
class EInvoiceSubmissionExecutor {

    private static final String ANONYMOUS_CONSUMER_TCKN = "11111111111";
    private static final String DEFAULT_UNIT = "C62";

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final EInvoiceGatewayPort eInvoiceGatewayPort;
    private final OwnerLookupPort ownerLookupPort;
    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceEInvoiceUpdatePort invoiceEInvoiceUpdatePort;

    @Value("${efatura.faturaentegrator.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    @Async
    @Transactional
    public void attemptSubmit(UUID submissionId) {
        EInvoiceSubmission submission = eInvoiceSubmissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            return;
        }

        OwnerSummary owner = ownerLookupPort.findSummaryById(submission.getOwnerId());

        if (!hasValidSurname(owner.fullName())) {
            submission.markFailed(
                "Sahibin adi soyadi eksik: \"" + owner.fullName() + "\". GIB e-Fatura'da soyad alani en az 2 "
                    + "karakter olmali -- lutfen sahip kaydini duzenleyip \"Ad Soyad\" alanina soyadi da ekleyin, "
                    + "sonra tekrar deneyin."
            );
            eInvoiceSubmissionRepository.save(submission);
            log.warn(
                "e-Fatura gonderimi engellendi (soyad eksik/gecersiz): submissionId={}, ownerId={}",
                submissionId, submission.getOwnerId()
            );
            return;
        }

        // Koprulme kurali: bu is @Async bir executor thread'inde calisiyor, ambient
        // TenantContext YOK. InvoiceLine (@TenantId'li) sorgusundan once context'i
        // submission'in kendi tenantId'siyle kurmali, sonra finally'de temizlemeliyiz
        // -- aksi halde sorgu root Session'da, yani FILTRESIZ calisir (bkz.
        // AppointmentReminderScheduler ile ayni desen).
        TenantContext.set(submission.getTenantId());
        List<EInvoiceLineItem> lines;
        try {
            lines = invoiceLineRepository.findByInvoiceId(submission.getInvoiceId()).stream()
                .map(this::toLineItem)
                .toList();
        } finally {
            TenantContext.clear();
        }
        String callbackUrl = callbackBaseUrl + "/api/v1/public/efatura/faturaentegrator/callback";

        EInvoiceSubmissionOutcome outcome = eInvoiceGatewayPort.submit(new EInvoiceSubmissionRequest(
            submission.getInvoiceId(), submission.getOwnerId(), submission.getDocumentType(),
            owner.fullName(), owner.address(), owner.city(), owner.district(), ANONYMOUS_CONSUMER_TCKN,
            submission.getTotalAmount(), submission.getTaxAmount(), lines, callbackUrl
        ));

        if (!outcome.success()) {
            submission.markFailed(outcome.message());
            log.warn("e-Fatura gonderimi basarisiz: submissionId={}, sebep={}", submissionId, outcome.message());
        } else if (outcome.finalResult()) {
            submission.markSubmitted(outcome.gibReference());
            invoiceEInvoiceUpdatePort.applyEInvoiceReference(submission.getInvoiceId(), outcome.gibReference());
        } else {
            submission.markAcceptedByProvider(outcome.gibReference());
            log.info(
                "e-Fatura saglayiciya iletildi, GIB resmilesmesi bekleniyor: submissionId={}, providerReference={}",
                submissionId, outcome.gibReference()
            );
        }
        eInvoiceSubmissionRepository.save(submission);
    }

    /**
     * GIB e-Fatura'da gercek kisi alicinin soyadi en az 2 karakter olmali
     * (bkz. faturaentegrator "Resmilestirme" hatasi: "Soyad(FamilyName)
     * alani 2 haneden az olamaz"). Sahip kaydinda tek kelimelik isim varsa
     * (soyad girilmemis), saglayiciya hic gondermeden erken basarisiz
     * kilariz -- boylece hem saglayici panelinde bos/hatali kayit birikmez
     * hem de personel MyVet arayuzunde acik, aksiyon alinabilir bir hata
     * gorur (bkz. "hale" test vakasi -- soyadsiz sahip kaydi).
     */
    private boolean hasValidSurname(String fullName) {
        if (fullName == null) {
            return false;
        }
        String trimmed = fullName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return false;
        }
        return trimmed.substring(lastSpace + 1).trim().length() >= 2;
    }

    private EInvoiceLineItem toLineItem(InvoiceLine line) {
        return new EInvoiceLineItem(
            line.getId().toString(), line.getDescription(), line.getQuantity(), DEFAULT_UNIT,
            line.getUnitPrice(), line.getVatRate()
        );
    }
}
