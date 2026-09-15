package com.vetos.modules.billing.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(UUID id);
    List<Invoice> findByTenantId(UUID tenantId);
    List<Invoice> findByOwnerId(UUID ownerId);
    List<Invoice> findByOwnerIdAndStatusIn(UUID ownerId, List<InvoiceStatus> statuses);
    Optional<Invoice> findByBoardingStayId(UUID boardingStayId);

    /**
     * Verilen kiracinin, [start, end) araliginda kesilmis ve durumu {@code statuses}
     * icinde olan faturalarinin toplam tutari ile adedi. Toplama veritabaninda
     * yapilir -- KPI hesaplamak icin tum fatura gecmisi belleğe cekilmez.
     */
    SalesAggregate sumIssuedBetween(UUID tenantId, Collection<InvoiceStatus> statuses, Instant start, Instant end);
}
