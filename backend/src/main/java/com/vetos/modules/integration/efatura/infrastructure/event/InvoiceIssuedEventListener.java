package com.vetos.modules.integration.efatura.infrastructure.event;

import com.vetos.modules.billing.domain.event.InvoiceIssuedEvent;
import com.vetos.modules.integration.efatura.application.QueueEInvoiceSubmissionUseCase;
import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Plan ozelligi (E_FATURA) kapaliysa GIB gonderimini sessizce atlar (spec 2026-10-12 plan ozellik kisitlamasi 4.4). */
@Component
@Slf4j
@RequiredArgsConstructor
class InvoiceIssuedEventListener {

    private final QueueEInvoiceSubmissionUseCase queueEInvoiceSubmissionUseCase;
    private final FeatureAccessPort featureAccess;

    @EventListener
    void onInvoiceIssued(InvoiceIssuedEvent event) {
        if (!featureAccess.isEnabled(event.tenantId(), Feature.E_FATURA)) {
            log.info("E-fatura modulu planda kapali, GIB gonderimi atlandi: invoiceId={}", event.invoiceId());
            return;
        }
        // GIB mukellef sorgu servisi bu ortamda baglanmadigindan, tum
        // aliciler bireysel (Pet Owner) kabul edilip E_ARSIV kullanilir
        // (@see EInvoiceDocumentType).
        queueEInvoiceSubmissionUseCase.execute(
            event.tenantId(), event.invoiceId(), event.ownerId(), EInvoiceDocumentType.E_ARSIV,
            event.totalAmount(), event.taxAmount()
        );
    }
}
