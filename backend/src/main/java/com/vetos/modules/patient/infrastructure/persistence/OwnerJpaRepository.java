package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Owner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface OwnerJpaRepository extends JpaRepository<Owner, UUID> {

    @Query(
        value = "SELECT * FROM owners o WHERE o.tenant_id = :tenantId " +
            "AND (o.full_name ILIKE CONCAT('%', :query, '%') OR o.phone ILIKE CONCAT('%', :query, '%'))",
        nativeQuery = true
    )
    List<Owner> searchByTenantAndQuery(@Param("tenantId") UUID tenantId, @Param("query") String query);

    @Query(
        value = "SELECT * FROM owners o WHERE o.tenant_id = :tenantId " +
            "AND (CAST(:nameContains AS text) IS NULL OR o.full_name ILIKE CONCAT('%', CAST(:nameContains AS text), '%')) " +
            "AND (CAST(:registeredFrom AS timestamptz) IS NULL OR o.created_at >= CAST(:registeredFrom AS timestamptz)) " +
            "AND (CAST(:registeredTo AS timestamptz) IS NULL OR o.created_at < CAST(:registeredTo AS timestamptz))",
        nativeQuery = true
    )
    List<Owner> findByTenantIdWithFilters(
        @Param("tenantId") UUID tenantId, @Param("nameContains") String nameContains,
        @Param("registeredFrom") Instant registeredFrom, @Param("registeredTo") Instant registeredTo
    );
}
