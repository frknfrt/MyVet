package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.event.InvoiceIssuedEvent;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IssueInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        BigDecimal totalVat = invoiceLineRepository.findByInvoiceId(invoiceId).stream()
            .map(InvoiceLine::getVatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.recalculateTax(totalVat);

        invoice.issue();
        invoiceRepository.save(invoice);

        eventPublisher.publish(new InvoiceIssuedEvent(
            invoice.getId(), invoice.getTenantId(), invoice.getOwnerId(), invoice.getTotalAmount(), invoice.getTaxAmount(), invoice.getIssuedAt()
        ));
    }
}
