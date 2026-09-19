package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.domain.*;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.modules.inventory.domain.StockDeductionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddInvoiceLineUseCase {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final StockDeductionPort stockDeductionPort;

    @Transactional
    public UUID execute(AddInvoiceLineCommand command) {
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
            .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));

        if (command.inventoryItemId() != null) {
            stockDeductionPort.deductForSale(command.inventoryItemId(), command.quantity(), invoice.getId());
        }

        InvoiceLine line = invoiceLineRepository.save(InvoiceLine.create(
            invoice.getTenantId(), invoice.getId(), command.description(), command.quantity(), command.unitPrice(),
            command.discountAmount(), command.vatRate(),
            command.serviceTypeId(), command.inventoryItemId(), InvoiceLineSource.MANUAL
        ));

        BigDecimal newTotal = invoiceLineRepository.findByInvoiceId(invoice.getId()).stream()
            .map(InvoiceLine::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.recalculateTotal(newTotal);
        invoiceRepository.save(invoice);

        return line.getId();
    }
}
