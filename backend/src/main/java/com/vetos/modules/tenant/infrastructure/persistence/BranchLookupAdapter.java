package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.BranchLookupPort;
import com.vetos.modules.tenant.domain.BranchSummary;
import com.vetos.modules.tenant.domain.exception.BranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class BranchLookupAdapter implements BranchLookupPort {

    private final BranchJpaRepository jpaRepository;

    @Override
    public BranchSummary findSummaryById(UUID branchId) {
        var branch = jpaRepository.findById(branchId)
            .orElseThrow(() -> new BranchNotFoundException(branchId));
        return new BranchSummary(branch.getId(), branch.getName());
    }

    @Override
    public List<BranchSummary> findAllByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantId(tenantId).stream()
            .map(branch -> new BranchSummary(branch.getId(), branch.getName()))
            .toList();
    }
}
