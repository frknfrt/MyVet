package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository jpaRepository;

    @Override
    public Tenant save(Tenant tenant) { return jpaRepository.save(tenant); }

    @Override
    public Optional<Tenant> findById(UUID id) { return jpaRepository.findById(id); }
}
