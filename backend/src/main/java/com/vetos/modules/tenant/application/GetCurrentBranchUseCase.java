package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.BranchOverview;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.modules.tenant.domain.exception.BranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetCurrentBranchUseCase {

    private final BranchRepository branchRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public BranchOverview execute(UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new BranchNotFoundException(branchId));
        Tenant tenant = tenantRepository.findById(branch.getTenantId())
            .orElseThrow(() -> new BranchNotFoundException(branchId));

        return new BranchOverview(
            branch.getId(), tenant.getId(), tenant.getName(), branch.getName(),
            branch.getAddress(), branch.getCity(), branch.getTimezone(), branch.getTarbilBranchCode()
        );
    }
}
