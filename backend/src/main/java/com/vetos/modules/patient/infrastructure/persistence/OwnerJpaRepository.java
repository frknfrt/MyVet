package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Owner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface OwnerJpaRepository extends JpaRepository<Owner, UUID> {

    @Query(
        value = "SELECT * FROM owners o WHERE o.tenant_id = :tenantId " +
            "AND (o.full_name ILIKE CONCAT('%', :query, '%') OR o.phone ILIKE CONCAT('%', :query, '%'))",
        nativeQuery = true
    )
    List<Owner> searchByTenantAndQuery(@Param("tenantId") UUID tenantId, @Param("query") String query);
}
