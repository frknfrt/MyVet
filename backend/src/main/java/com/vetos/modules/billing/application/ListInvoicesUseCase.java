package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.InvoiceSummary;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListInvoicesUseCase {

    private final InvoiceRepository invoiceRepository;
    private final OwnerLookupPort ownerLookupPort;

    @Transactional(readOnly = true)
    public List<InvoiceSummary> execute(UUID tenantId) {
        return invoiceRepository.findByTenantId(tenantId).stream()
            .map(invoice -> new InvoiceSummary(
                invoice.getId(), invoice.getOwnerId(), ownerLookupPort.findSummaryById(invoice.getOwnerId()).fullName(),
                invoice.getTotalAmount(), invoice.getStatus(), invoice.getIssuedAt()
            ))
            .sorted(Comparator.comparing(InvoiceSummary::issuedAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
            .toList();
    }
}
