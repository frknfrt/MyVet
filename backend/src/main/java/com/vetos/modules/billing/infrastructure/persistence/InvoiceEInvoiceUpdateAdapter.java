package com.vetos.modules.billing.infrastructure.persistence;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceEInvoiceUpdatePort;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class InvoiceEInvoiceUpdateAdapter implements InvoiceEInvoiceUpdatePort {

    private final InvoiceJpaRepository jpaRepository;

    @Override
    @Transactional
    public void applyEInvoiceReference(UUID invoiceId, String eInvoiceRef) {
        Invoice invoice = jpaRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        invoice.applyEInvoiceReference(eInvoiceRef);
        jpaRepository.save(invoice);
    }
}
