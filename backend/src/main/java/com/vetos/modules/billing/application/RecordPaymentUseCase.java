package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.domain.*;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordPaymentUseCase {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public UUID execute(RecordPaymentCommand command) {
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
            .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));

        Payment payment = paymentRepository.save(Payment.record(
            invoice.getTenantId(), invoice.getId(), command.method(), command.amount(), command.pspRef()
        ));

        BigDecimal totalPaid = paymentRepository.findByInvoiceId(invoice.getId()).stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.applyPaymentStatus(totalPaid);
        invoiceRepository.save(invoice);

        return payment.getId();
    }
}
