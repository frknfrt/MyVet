package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilSyncLogRepository {
    TarbilSyncLog save(TarbilSyncLog log);
    Optional<TarbilSyncLog> findById(UUID id);
    /** TARBIL_SYNC_LOG'da tenant_id yok (er-diagram.mermaid) -- patient -> owner uzerinden filtrelenir. */
    List<TarbilSyncLog> findByTenantId(UUID tenantId);
    List<TarbilSyncLog> findByPatientId(UUID patientId);
}
