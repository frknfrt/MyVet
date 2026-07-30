package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface PatientJpaRepository extends JpaRepository<Patient, UUID> {

    List<Patient> findByOwnerId(UUID ownerId);

    @Query(
        value = "SELECT p.* FROM patients p JOIN owners o ON p.owner_id = o.id " +
            "WHERE o.tenant_id = :tenantId " +
            "AND (p.name ILIKE CONCAT('%', :query, '%') OR o.full_name ILIKE CONCAT('%', :query, '%'))",
        nativeQuery = true
    )
    List<Patient> searchByNameOrOwner(@Param("tenantId") UUID tenantId, @Param("query") String query);
}
