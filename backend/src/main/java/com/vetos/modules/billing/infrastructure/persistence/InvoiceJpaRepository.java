package com.vetos.modules.billing.infrastructure.persistence;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface InvoiceJpaRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantId(UUID tenantId);
    List<Invoice> findByOwnerId(UUID ownerId);
    List<Invoice> findByOwnerIdAndStatusIn(UUID ownerId, List<InvoiceStatus> statuses);
}
