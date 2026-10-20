package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoidInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;
    private final AuditPort auditPort;

    @Transactional
    public void execute(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        invoice.voidInvoice();
        invoiceRepository.save(invoice);
        auditPort.record(AuditRecord.of(AuditAction.INVOICE_VOIDED, "INVOICE", invoiceId,
            AuditLabels.shortId(invoiceId), "Tutar: " + AuditLabels.money(invoice.getTotalAmount())));
    }
}
