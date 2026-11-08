package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.domain.*;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.modules.billing.domain.exception.InvoiceInvalidStateException;
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
        Invoice invoice = invoiceRepository.findByIdForUpdate(command.invoiceId())
            .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));

        if (command.amount() == null || command.method() == null || command.amount().signum() <= 0
            || command.amount().stripTrailingZeros().scale() > 2) {
            throw new InvoiceInvalidStateException(invoice.getId(), "Tahsilat pozitif ve en fazla iki ondalık basamaklı olmalıdır");
        }
        var existing = paymentRepository.findByInvoiceId(invoice.getId());
        if (command.requestId() != null) {
            for (Payment previous : existing) {
                if (command.requestId().equals(previous.getRequestId())) {
                    if (previous.getAmount().compareTo(command.amount()) != 0 || previous.getMethod() != command.method()
                        || !java.util.Objects.equals(previous.getPspRef(), command.pspRef())) {
                        throw new InvoiceInvalidStateException(invoice.getId(), "İşlem anahtarı farklı tahsilatta kullanılmış");
                    }
                    return previous.getId();
                }
            }
        }
        BigDecimal totalPaid = existing.stream().map(Payment::getAmount).reduce(command.amount(), BigDecimal::add);
        if (command.amount().signum() <= 0 || totalPaid.compareTo(invoice.getTotalAmount()) > 0) {
            throw new InvoiceInvalidStateException(invoice.getId(), "Tahsilat tutarı kalan bakiye ile uyumsuz");
        }
        invoice.applyPaymentStatus(totalPaid);
        Payment payment = paymentRepository.save(Payment.record(invoice.getTenantId(), invoice.getId(),
            command.method(), command.amount(), command.pspRef(), command.requestId()));
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
