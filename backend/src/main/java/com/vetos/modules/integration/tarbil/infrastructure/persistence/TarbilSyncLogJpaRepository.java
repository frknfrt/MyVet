package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface TarbilSyncLogJpaRepository extends JpaRepository<TarbilSyncLog, UUID> {

    List<TarbilSyncLog> findByPatientId(UUID patientId);

    @Query(
        value = "SELECT t.* FROM tarbil_sync_log t " +
            "JOIN patients p ON t.patient_id = p.id " +
            "JOIN owners o ON p.owner_id = o.id " +
            "WHERE o.tenant_id = :tenantId",
        nativeQuery = true
    )
    List<TarbilSyncLog> findByTenantId(@Param("tenantId") UUID tenantId);
}
