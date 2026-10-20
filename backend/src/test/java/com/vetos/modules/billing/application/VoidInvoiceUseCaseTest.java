package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoidInvoiceUseCaseTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private AuditPort auditPort;

    private VoidInvoiceUseCase useCase() {
        return new VoidInvoiceUseCase(invoiceRepository, auditPort);
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(invoice, "id", id);
        invoice.recalculateTotal(new BigDecimal("1250"));
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(invoice));

        useCase().execute(id);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.INVOICE_VOIDED);
        assertThat(rec.targetType()).isEqualTo("INVOICE");
        assertThat(rec.targetId()).isEqualTo(id);
        assertThat(rec.targetLabel()).isEqualTo(id.toString().substring(0, 8));
        assertThat(rec.summary()).isEqualTo("Tutar: 1.250,00 ₺");
    }

    @Test
    void should_notRecord_whenInvoiceNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(id)).isInstanceOf(InvoiceNotFoundException.class);
        verifyNoInteractions(auditPort);
    }
}
