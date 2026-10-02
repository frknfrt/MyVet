package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilValueMappingRepository {
    TarbilValueMapping save(TarbilValueMapping mapping);
    Optional<TarbilValueMapping> findById(UUID id);
    Optional<TarbilValueMapping> findByTenantIdAndKindAndVetlyKey(UUID tenantId, TarbilMappingKind kind, String vetlyKey);
    List<TarbilValueMapping> findByTenantId(UUID tenantId);
    void delete(TarbilValueMapping mapping);
}
