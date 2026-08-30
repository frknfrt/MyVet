package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import com.vetos.modules.platformadmin.domain.TenantSignupRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TenantSignupRequestRepositoryAdapter implements TenantSignupRequestRepository {

    private final TenantSignupRequestJpaRepository jpaRepository;

    @Override
    public TenantSignupRequest save(TenantSignupRequest request) { return jpaRepository.save(request); }

    @Override
    public Optional<TenantSignupRequest> findById(UUID id) { return jpaRepository.findById(id); }
}
