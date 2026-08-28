package com.vetos.modules.platformadmin.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformInvoiceRepository {
    PlatformInvoice save(PlatformInvoice invoice);
    Optional<PlatformInvoice> findById(UUID id);
    Optional<PlatformInvoice> findByTenantIdAndPeriodStart(UUID tenantId, LocalDate periodStart);
    List<PlatformInvoice> findByTenantId(UUID tenantId);
    List<PlatformInvoice> findByStatusAndDueDate(PlatformInvoiceStatus status, LocalDate dueDate);
    List<PlatformInvoice> findByStatusAndDueDateBefore(PlatformInvoiceStatus status, LocalDate date);
}
