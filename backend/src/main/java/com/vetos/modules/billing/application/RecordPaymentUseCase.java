package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.domain.*;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
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
    private final AuditPort auditPort;

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

        auditPort.record(AuditRecord.of(AuditAction.INVOICE_PAYMENT_RECORDED, "INVOICE", invoice.getId(),
            AuditLabels.shortId(invoice.getId()), AuditLabels.money(command.amount()) + " — " + methodLabel(command.method())));

        return payment.getId();
    }

    /** Odeme yontemi Turkce etiketi; bilinmeyen sabit enum adiyla yazilir. */
    private static String methodLabel(PaymentMethod method) {
        if (method == null) return null;
        return switch (method) {
            case CASH -> "Nakit";
            case CARD -> "Kart";
            case TEXT_TO_PAY -> "Linkle ödeme";
            case INSTALLMENT -> "Taksit";
            default -> method.name();
        };
    }
}
