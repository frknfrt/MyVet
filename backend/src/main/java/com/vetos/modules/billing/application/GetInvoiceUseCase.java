package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.InvoiceDetail;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.Payment;
import com.vetos.modules.billing.domain.PaymentRepository;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetInvoiceUseCase {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final PaymentRepository paymentRepository;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional(readOnly = true)
    public InvoiceDetail execute(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        return toDetail(invoice);
    }

    InvoiceDetail toDetail(Invoice invoice) {
        var owner = ownerLookupPort.findSummaryById(invoice.getOwnerId());

        var lines = invoiceLineRepository.findByInvoiceId(invoice.getId()).stream()
            .map(l -> new InvoiceDetail.Line(
                l.getId(), l.getDescription(), l.getQuantity(), l.getUnitPrice(),
                l.getDiscountAmount(), l.getVatRate(), l.getVatAmount(),
                l.getLineTotal(), l.getSource()
            ))
            .toList();

        var payments = paymentRepository.findByInvoiceId(invoice.getId());
        var paymentRecords = payments.stream()
            .map(p -> new InvoiceDetail.PaymentRecord(p.getId(), p.getMethod(), p.getAmount(), p.getPaidAt()))
            .toList();
        BigDecimal paidAmount = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new InvoiceDetail(
            invoice.getId(), invoice.getOwnerId(), owner.fullName(), invoice.getEncounterId(), invoice.getEInvoiceRef(),
            invoice.getTotalAmount(), invoice.getTaxAmount(), paidAmount, invoice.getStatus(), invoice.getIssuedAt(),
            lines, paymentRecords
        );
    }
}
