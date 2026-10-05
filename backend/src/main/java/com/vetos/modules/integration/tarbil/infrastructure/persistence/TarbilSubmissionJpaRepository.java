package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TarbilSubmissionJpaRepository extends JpaRepository<TarbilSubmission, UUID> {
    List<TarbilSubmission> findByTenantId(UUID tenantId);
    List<TarbilSubmission> findByTenantIdAndStatusOrderByQueuedAtAsc(UUID tenantId, TarbilSyncStatus status);
    Optional<TarbilSubmission> findByDocumentTypeAndSourceId(TarbilDocumentType documentType, UUID sourceId);
    List<TarbilSubmission> findByStatusAndQueuedAtBeforeOrderByQueuedAtAsc(TarbilSyncStatus status, Instant cutoff, Pageable page);
    long countByTenantIdAndStatusAndQueuedAtBefore(UUID tenantId, TarbilSyncStatus status, Instant cutoff);
}
