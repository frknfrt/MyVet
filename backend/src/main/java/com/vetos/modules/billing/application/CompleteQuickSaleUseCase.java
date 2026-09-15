package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.application.dto.CompleteQuickSaleCommand;
import com.vetos.modules.billing.application.dto.QuickSaleLineCommand;
import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.exception.InvoiceNotFoundException;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tek ekranli "Hizli Satis" akisi -- kayitli ya da anonim bir musteriye,
 * tek transaction'da fatura acar, kalemleri ekler (stok duser), keser ve
 * tam odemeyi alir. Herhangi bir adim (orn. stok yetersiz) hata verirse
 * tum islem geri alinir. Bkz. docs/superpowers/specs/2026-09-14-hizli-satis-design.md S4.4.
 */
@Service
@RequiredArgsConstructor
public class CompleteQuickSaleUseCase {

    private final CreateManualInvoiceUseCase createManualInvoiceUseCase;
    private final AddInvoiceLineUseCase addInvoiceLineUseCase;
    private final IssueInvoiceUseCase issueInvoiceUseCase;
    private final RecordPaymentUseCase recordPaymentUseCase;
    private final InvoiceRepository invoiceRepository;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional
    public UUID execute(CompleteQuickSaleCommand command) {
        UUID resolvedOwnerId = command.ownerId() != null
            ? command.ownerId()
            : ownerLookupPort.getOrCreateAnonymousOwnerId(com.vetos.platform.tenancy.TenantContext.current());

        UUID invoiceId = createManualInvoiceUseCase.execute(command.branchId(), resolvedOwnerId, command.staffUserId());

        for (QuickSaleLineCommand line : command.lines()) {
            addInvoiceLineUseCase.execute(new AddInvoiceLineCommand(
                invoiceId, line.description(), line.quantity(), line.unitPrice(),
                BigDecimal.ZERO, line.vatRate(), null, line.inventoryItemId()
            ));
        }

        issueInvoiceUseCase.execute(invoiceId);

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        recordPaymentUseCase.execute(new RecordPaymentCommand(invoiceId, command.paymentMethod(), invoice.getTotalAmount(), null));

        return invoiceId;
    }
}
