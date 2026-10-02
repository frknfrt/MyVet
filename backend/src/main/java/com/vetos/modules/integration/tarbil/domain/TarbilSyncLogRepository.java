package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilSyncLogRepository {
    TarbilSyncLog save(TarbilSyncLog log);
    Optional<TarbilSyncLog> findById(UUID id);
    List<TarbilSyncLog> findByTenantId(UUID tenantId);
    List<TarbilSyncLog> findByTenantIdAndStatus(UUID tenantId, TarbilSyncStatus status);
    Optional<TarbilSyncLog> findByVaccinationRecordId(UUID vaccinationRecordId);
}
