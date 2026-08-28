package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PlatformInvoiceRepositoryAdapter implements PlatformInvoiceRepository {

    private final PlatformInvoiceJpaRepository jpaRepository;

    @Override
    public PlatformInvoice save(PlatformInvoice invoice) { return jpaRepository.save(invoice); }

    @Override
    public Optional<PlatformInvoice> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<PlatformInvoice> findByTenantIdAndPeriodStart(UUID tenantId, LocalDate periodStart) {
        return jpaRepository.findByTenantIdAndPeriodStart(tenantId, periodStart);
    }

    @Override
    public List<PlatformInvoice> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<PlatformInvoice> findByStatusAndDueDate(PlatformInvoiceStatus status, LocalDate dueDate) {
        return jpaRepository.findByStatusAndDueDate(status, dueDate);
    }

    @Override
    public List<PlatformInvoice> findByStatusAndDueDateBefore(PlatformInvoiceStatus status, LocalDate date) {
        return jpaRepository.findByStatusAndDueDateBefore(status, date);
    }
}
