package com.vetos.modules.billing.domain;

import java.util.List;
import java.util.UUID;

public interface InvoiceLineRepository {
    InvoiceLine save(InvoiceLine line);
    List<InvoiceLine> findByInvoiceId(UUID invoiceId);
}
