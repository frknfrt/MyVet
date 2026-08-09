package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.BranchOverview;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListBranchesUseCase {

    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public List<BranchOverview> execute(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        return branchRepository.findByTenantId(tenantId).stream()
            .map(branch -> new BranchOverview(
                branch.getId(), tenant.getId(), tenant.getName(), branch.getName(),
                branch.getAddress(), branch.getCity(), branch.getTimezone(), branch.getTarbilBranchCode()
            ))
            .toList();
    }
}
