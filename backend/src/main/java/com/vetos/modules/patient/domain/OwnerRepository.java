package com.vetos.modules.patient.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnerRepository {
    Owner save(Owner owner);
    Optional<Owner> findById(UUID id);
    List<Owner> searchByTenantAndQuery(UUID tenantId, String query);
}
