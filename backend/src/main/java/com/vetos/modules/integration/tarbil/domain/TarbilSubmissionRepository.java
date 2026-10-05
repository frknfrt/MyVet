package com.vetos.modules.integration.tarbil.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilSubmissionRepository {
    TarbilSubmission save(TarbilSubmission submission);
    Optional<TarbilSubmission> findById(UUID id);
    /** Tum kiracilarda, cutoff'tan once kuyruga girmis hala bekleyen aktarimlar (platform admin saglik paneli). */
    List<TarbilSubmission> findPendingQueuedBefore(Instant cutoff, int limit);
    long countPendingQueuedBefore(UUID tenantId, Instant cutoff);
    List<TarbilSubmission> findByTenantId(UUID tenantId);
    List<TarbilSubmission> findByTenantIdAndStatus(UUID tenantId, TarbilSyncStatus status);
    Optional<TarbilSubmission> findByDocumentTypeAndSourceId(TarbilDocumentType documentType, UUID sourceId);
}
