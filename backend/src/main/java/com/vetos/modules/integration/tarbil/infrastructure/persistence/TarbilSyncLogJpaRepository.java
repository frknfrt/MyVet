package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TarbilSyncLogJpaRepository extends JpaRepository<TarbilSyncLog, UUID> {
    List<TarbilSyncLog> findByTenantId(UUID tenantId);
    List<TarbilSyncLog> findByTenantIdAndStatusOrderByQueuedAtAsc(UUID tenantId, TarbilSyncStatus status);
    Optional<TarbilSyncLog> findByVaccinationRecordId(UUID vaccinationRecordId);
}
