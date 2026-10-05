package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilSubmissionRepositoryAdapter implements TarbilSubmissionRepository {

    private final TarbilSubmissionJpaRepository jpaRepository;

    @Override public TarbilSubmission save(TarbilSubmission submission) { return jpaRepository.save(submission); }

    @Override public Optional<TarbilSubmission> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<TarbilSubmission> findPendingQueuedBefore(java.time.Instant cutoff, int limit) {
        return jpaRepository.findByStatusAndQueuedAtBeforeOrderByQueuedAtAsc(
            com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus.PENDING, cutoff,
            org.springframework.data.domain.PageRequest.of(0, limit));
    }

    @Override
    public long countPendingQueuedBefore(UUID tenantId, java.time.Instant cutoff) {
        return jpaRepository.countByTenantIdAndStatusAndQueuedAtBefore(
            tenantId, com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus.PENDING, cutoff);
    }

    @Override public List<TarbilSubmission> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<TarbilSubmission> findByTenantIdAndStatus(UUID tenantId, TarbilSyncStatus status) {
        return jpaRepository.findByTenantIdAndStatusOrderByQueuedAtAsc(tenantId, status);
    }

    @Override
    public Optional<TarbilSubmission> findByDocumentTypeAndSourceId(TarbilDocumentType documentType, UUID sourceId) {
        return jpaRepository.findByDocumentTypeAndSourceId(documentType, sourceId);
    }
}
