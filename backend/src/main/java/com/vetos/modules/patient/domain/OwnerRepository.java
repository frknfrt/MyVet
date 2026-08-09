package com.vetos.modules.patient.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnerRepository {
    Owner save(Owner owner);
    Optional<Owner> findById(UUID id);
    List<Owner> searchByTenantAndQuery(UUID tenantId, String query);
    List<Owner> findByTenantIdWithFilters(UUID tenantId, String nameContains, Instant registeredFrom, Instant registeredTo);
}
