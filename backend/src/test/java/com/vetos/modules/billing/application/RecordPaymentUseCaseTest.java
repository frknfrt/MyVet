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
        when(invoiceRepository.findByIdForUpdate(id)).thenReturn(Optional.of(invoice));
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
    void sameRequestIsReturnedWithoutAnotherPaymentOrAudit() {
        UUID invoiceId = UUID.randomUUID(), requestId = UUID.randomUUID(), paymentId = UUID.randomUUID();
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        var previous = com.vetos.modules.billing.domain.Payment.record(invoice.getTenantId(), invoiceId,
            PaymentMethod.CASH, BigDecimal.TEN, null, requestId);
        ReflectionTestUtils.setField(previous, "id", paymentId);
        when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(previous));
        assertThat(useCase().execute(new RecordPaymentCommand(invoiceId, PaymentMethod.CASH,
            BigDecimal.TEN, null, requestId))).isEqualTo(paymentId);
        verify(paymentRepository, org.mockito.Mockito.never()).save(any());
        verifyNoInteractions(auditPort);
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
        when(invoiceRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(new RecordPaymentCommand(id, PaymentMethod.CASH, BigDecimal.TEN, null)))
            .isInstanceOf(InvoiceNotFoundException.class);
        verifyNoInteractions(auditPort);
    }

    @Test
    void changedPayloadCannotReuseRequestId() {
        var invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        UUID id = UUID.randomUUID(), requestId = UUID.randomUUID();
        ReflectionTestUtils.setField(invoice, "id", id);
        var payment = com.vetos.modules.billing.domain.Payment.record(invoice.getTenantId(), id, PaymentMethod.CASH,
            BigDecimal.TEN, null, requestId);
        when(invoiceRepository.findByIdForUpdate(id)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(id)).thenReturn(List.of(payment));
        assertThatThrownBy(() -> useCase().execute(new RecordPaymentCommand(id, PaymentMethod.CASH,
            BigDecimal.ONE, null, requestId))).isInstanceOf(com.vetos.modules.billing.domain.exception.InvoiceInvalidStateException.class);
        verify(paymentRepository, org.mockito.Mockito.never()).save(any());
        verifyNoInteractions(auditPort);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "-1", "0.001", "1001"})
    void invalidOrExcessPaymentCannotBeSaved(String amount) {
        var invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(invoice, "id", id);
        invoice.recalculateTotal(new BigDecimal("1000"));
        invoice.issue();
        when(invoiceRepository.findByIdForUpdate(id)).thenReturn(Optional.of(invoice));
        org.mockito.Mockito.lenient().when(paymentRepository.findByInvoiceId(id)).thenReturn(List.of());
        assertThatThrownBy(() -> useCase().execute(new RecordPaymentCommand(id, PaymentMethod.CASH,
            new BigDecimal(amount), null))).isInstanceOf(com.vetos.modules.billing.domain.exception.InvoiceInvalidStateException.class);
        verify(paymentRepository, org.mockito.Mockito.never()).save(any());
        verifyNoInteractions(auditPort);
    }
}
