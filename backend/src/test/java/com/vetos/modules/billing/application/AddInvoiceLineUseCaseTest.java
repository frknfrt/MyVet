package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.inventory.domain.StockDeductionPort;
import com.vetos.modules.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddInvoiceLineUseCaseTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private StockDeductionPort stockDeductionPort;

    private AddInvoiceLineUseCase useCase;

    private AddInvoiceLineCommand aCommand(UUID invoiceId, UUID inventoryItemId) {
        return new AddInvoiceLineCommand(
            invoiceId, "Kedi Maması", 2, BigDecimal.valueOf(150),
            BigDecimal.ZERO, BigDecimal.ZERO, null, inventoryItemId
        );
    }

    @Test
    void should_deductStock_when_inventoryItemIdProvided() {
        useCase = new AddInvoiceLineUseCase(invoiceRepository, invoiceLineRepository, stockDeductionPort);
        UUID invoiceId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceLineRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());

        useCase.execute(aCommand(invoiceId, itemId));

        verify(stockDeductionPort).deductForSale(itemId, 2, invoiceId);
    }

    @Test
    void should_notCallStockDeduction_when_inventoryItemIdNull() {
        useCase = new AddInvoiceLineUseCase(invoiceRepository, invoiceLineRepository, stockDeductionPort);
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceLineRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());

        useCase.execute(aCommand(invoiceId, null));

        verifyNoInteractions(stockDeductionPort);
    }

    @Test
    void should_propagateInsufficientStock_and_notAddLine() {
        useCase = new AddInvoiceLineUseCase(invoiceRepository, invoiceLineRepository, stockDeductionPort);
        UUID invoiceId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        ReflectionTestUtils.setField(invoice, "id", invoiceId);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        doThrow(new InsufficientStockException("Kedi Maması", 2, 0)).when(stockDeductionPort).deductForSale(itemId, 2, invoiceId);

        assertThatThrownBy(() -> useCase.execute(aCommand(invoiceId, itemId)))
            .isInstanceOf(InsufficientStockException.class);

        verify(invoiceLineRepository, never()).save(any());
    }
}
