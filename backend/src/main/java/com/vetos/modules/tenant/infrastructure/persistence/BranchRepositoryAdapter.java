package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class BranchRepositoryAdapter implements BranchRepository {

    private final BranchJpaRepository jpaRepository;

    @Override
    public Branch save(Branch branch) { return jpaRepository.save(branch); }

    @Override
    public Optional<Branch> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Branch> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }
}
