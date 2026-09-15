package com.vetos.modules.billing.infrastructure.persistence;

import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.SalesAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface InvoiceJpaRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantId(UUID tenantId);
    List<Invoice> findByOwnerId(UUID ownerId);
    List<Invoice> findByOwnerIdAndStatusIn(UUID ownerId, List<InvoiceStatus> statuses);
    Optional<Invoice> findByBoardingStayId(UUID boardingStayId);

    @Query(
        "SELECT new com.vetos.modules.billing.domain.SalesAggregate(SUM(i.totalAmount), COUNT(i)) " +
            "FROM Invoice i WHERE i.tenantId = :tenantId AND i.status IN :statuses " +
            "AND i.issuedAt >= :start AND i.issuedAt < :end"
    )
    SalesAggregate sumIssuedBetween(
        @Param("tenantId") UUID tenantId, @Param("statuses") Collection<InvoiceStatus> statuses,
        @Param("start") Instant start, @Param("end") Instant end
    );
}
