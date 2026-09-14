package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class OwnerRepositoryAdapter implements OwnerRepository {

    private final OwnerJpaRepository jpaRepository;

    @Override
    public Owner save(Owner owner) { return jpaRepository.save(owner); }

    @Override
    public Optional<Owner> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Owner> searchByTenantAndQuery(UUID tenantId, String query) {
        return jpaRepository.searchByTenantAndQuery(tenantId, query);
    }

    @Override
    public List<Owner> findByTenantIdWithFilters(UUID tenantId, String nameContains, Instant registeredFrom, Instant registeredTo) {
        return jpaRepository.findByTenantIdWithFilters(tenantId, nameContains, registeredFrom, registeredTo);
    }

    @Override
    public Optional<Owner> findAnonymousPlaceholder(UUID tenantId) {
        return jpaRepository.findAnonymousPlaceholder(tenantId);
    }
}
