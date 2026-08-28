package com.vetos.modules.platformadmin.domain;

import java.util.Optional;
import java.util.UUID;

public interface PlatformPaymentRepository {
    PlatformPayment save(PlatformPayment payment);
    Optional<PlatformPayment> findByInvoiceId(UUID invoiceId);
}
