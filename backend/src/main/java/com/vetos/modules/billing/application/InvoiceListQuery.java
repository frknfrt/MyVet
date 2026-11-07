package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.InvoiceSummary;
import java.util.List;
import java.util.UUID;

public interface InvoiceListQuery {
    List<InvoiceSummary> page(UUID tenantId, UUID ownerId, long offset, int limit);
}
