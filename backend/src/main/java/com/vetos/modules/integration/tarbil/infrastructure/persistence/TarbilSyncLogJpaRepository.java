package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface TarbilSyncLogJpaRepository extends JpaRepository<TarbilSyncLog, UUID> {

    List<TarbilSyncLog> findByPatientId(UUID patientId);

    List<TarbilSyncLog> findByTenantId(UUID tenantId);

    List<TarbilSyncLog> findTop200ByStatusOrderByAttemptedAtDesc(TarbilSyncStatus status);

    @Query(value = """
        SELECT * FROM tarbil_sync_log
        WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= :now
        ORDER BY next_retry_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<TarbilSyncLog> claimDueForRetry(@Param("now") Instant now, @Param("limit") int limit);
}
