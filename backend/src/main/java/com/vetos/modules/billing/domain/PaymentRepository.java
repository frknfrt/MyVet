package com.vetos.modules.billing.domain;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);
    List<Payment> findByInvoiceId(UUID invoiceId);
}
