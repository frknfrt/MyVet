package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilSubmissionRepository {
    TarbilSubmission save(TarbilSubmission submission);
    Optional<TarbilSubmission> findById(UUID id);
    List<TarbilSubmission> findByTenantId(UUID tenantId);
    List<TarbilSubmission> findByTenantIdAndStatus(UUID tenantId, TarbilSyncStatus status);
    Optional<TarbilSubmission> findByDocumentTypeAndSourceId(TarbilDocumentType documentType, UUID sourceId);
}
