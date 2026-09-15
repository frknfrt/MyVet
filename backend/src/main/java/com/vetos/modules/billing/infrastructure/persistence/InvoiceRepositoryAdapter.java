package com.vetos.modules.billing.infrastructure.persistence;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.SalesAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class InvoiceRepositoryAdapter implements InvoiceRepository {

    private final InvoiceJpaRepository jpaRepository;

    @Override
    public Invoice save(Invoice invoice) { return jpaRepository.save(invoice); }

    @Override
    public Optional<Invoice> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Invoice> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<Invoice> findByOwnerId(UUID ownerId) { return jpaRepository.findByOwnerId(ownerId); }

    @Override
    public List<Invoice> findByOwnerIdAndStatusIn(UUID ownerId, List<InvoiceStatus> statuses) {
        return jpaRepository.findByOwnerIdAndStatusIn(ownerId, statuses);
    }

    @Override
    public Optional<Invoice> findByBoardingStayId(UUID boardingStayId) {
        return jpaRepository.findByBoardingStayId(boardingStayId);
    }

    @Override
    public SalesAggregate sumIssuedBetween(
        UUID tenantId, Collection<InvoiceStatus> statuses, Instant start, Instant end
    ) {
        return jpaRepository.sumIssuedBetween(tenantId, statuses, start, end);
    }
}
