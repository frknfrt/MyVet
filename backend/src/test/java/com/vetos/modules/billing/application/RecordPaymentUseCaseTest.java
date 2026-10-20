package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.PaymentMethod;
import com.vetos.modules.billing.domain.PaymentRepository;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordPaymentUseCaseTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AuditPort auditPort;

    private RecordPaymentUseCase useCase() {
        return new RecordPaymentUseCase(invoiceRepository, paymentRepository, auditPort);
    }

    private AuditRecord pay(PaymentMethod method, String amount) {
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(invoice, "id", id);
        invoice.recalculateTotal(new BigDecimal("1000"));
        invoice.issue();
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(invoice));
        when(paymentRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        when(paymentRepository.findByInvoiceId(id)).thenReturn(List.of());

        useCase().execute(new RecordPaymentCommand(id, method, new BigDecimal(amount), null));

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.INVOICE_PAYMENT_RECORDED);
        assertThat(rec.targetType()).isEqualTo("INVOICE");
        assertThat(rec.targetId()).isEqualTo(id);
        assertThat(rec.targetLabel()).isEqualTo(id.toString().substring(0, 8));
        return rec;
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        assertThat(pay(PaymentMethod.CASH, "450.5").summary()).isEqualTo("450,50 ₺ — Nakit");
    }

    @Test
    void should_mapCardToTurkishLabel() {
        assertThat(pay(PaymentMethod.CARD, "10").summary()).isEqualTo("10,00 ₺ — Kart");
    }

    @Test
    void should_notRecord_whenInvoiceNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(new RecordPaymentCommand(id, PaymentMethod.CASH, BigDecimal.TEN, null)))
            .isInstanceOf(InvoiceNotFoundException.class);
        verifyNoInteractions(auditPort);
    }
}
