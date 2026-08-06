package com.vetos.modules.billing.domain;

import java.util.UUID;

/**
 * integration/efatura modulunun e-Fatura/e-Arsiv gonderimi basarili
 * oldugunda GIB referansini ilgili faturaya yazmasi icin dar amacli yazma
 * portu (@docs/architecture.md Bolum 3 -- Interface Segregation: genis bir
 * InvoiceRepository yerine tek metotluk ihtiyaca gore bolunmus port).
 */
public interface InvoiceEInvoiceUpdatePort {
    void applyEInvoiceReference(UUID invoiceId, String eInvoiceRef);
}
