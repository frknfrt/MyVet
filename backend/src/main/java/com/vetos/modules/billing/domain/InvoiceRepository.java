package com.vetos.modules.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(UUID id);
    List<Invoice> findByTenantId(UUID tenantId);
    List<Invoice> findByOwnerId(UUID ownerId);
    List<Invoice> findByOwnerIdAndStatusIn(UUID ownerId, List<InvoiceStatus> statuses);
}
