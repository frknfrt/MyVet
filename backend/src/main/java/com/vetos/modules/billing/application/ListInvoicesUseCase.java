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

    private final InvoiceListQuery query;

    @Transactional(readOnly = true)
    public List<InvoiceSummary> execute(UUID tenantId, UUID ownerId, int page, int size) {
        return query.page(tenantId, ownerId, (long) page * size, size);
    }
}
