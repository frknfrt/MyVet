package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlatformInvoiceJpaRepository extends JpaRepository<PlatformInvoice, UUID> {
    Optional<PlatformInvoice> findByTenantIdAndPeriodStart(UUID tenantId, LocalDate periodStart);
    List<PlatformInvoice> findByTenantId(UUID tenantId);
    List<PlatformInvoice> findByStatusAndDueDate(PlatformInvoiceStatus status, LocalDate dueDate);
    List<PlatformInvoice> findByStatusAndDueDateBefore(PlatformInvoiceStatus status, LocalDate date);
}
