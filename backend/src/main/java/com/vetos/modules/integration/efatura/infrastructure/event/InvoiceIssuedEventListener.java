package com.vetos.modules.integration.efatura.infrastructure.event;

import com.vetos.modules.billing.domain.event.InvoiceIssuedEvent;
import com.vetos.modules.integration.efatura.application.QueueEInvoiceSubmissionUseCase;
import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InvoiceIssuedEventListener {

    private final QueueEInvoiceSubmissionUseCase queueEInvoiceSubmissionUseCase;

    @EventListener
    void onInvoiceIssued(InvoiceIssuedEvent event) {
        // GIB mukellef sorgu servisi bu ortamda baglanmadigindan, tum
        // aliciler bireysel (Pet Owner) kabul edilip E_ARSIV kullanilir
        // (@see EInvoiceDocumentType).
        queueEInvoiceSubmissionUseCase.execute(
            event.tenantId(), event.invoiceId(), event.ownerId(), EInvoiceDocumentType.E_ARSIV,
            event.totalAmount(), event.taxAmount()
        );
    }
}
