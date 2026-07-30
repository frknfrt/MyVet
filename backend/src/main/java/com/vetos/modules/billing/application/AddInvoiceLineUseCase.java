package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.domain.*;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
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

    @Transactional
    public UUID execute(AddInvoiceLineCommand command) {
        Invoice invoice = invoiceRepository.findById(command.invoiceId())
            .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));

        InvoiceLine line = invoiceLineRepository.save(InvoiceLine.create(
            invoice.getId(), command.description(), command.quantity(), command.unitPrice(),
            command.serviceTypeId(), null, InvoiceLineSource.MANUAL
        ));

        BigDecimal newTotal = invoiceLineRepository.findByInvoiceId(invoice.getId()).stream()
            .map(InvoiceLine::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        invoice.recalculateTotal(newTotal);
        invoiceRepository.save(invoice);

        return line.getId();
    }
}
