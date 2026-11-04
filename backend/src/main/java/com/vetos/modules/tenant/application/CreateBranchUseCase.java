package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.CreateBranchCommand;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.platform.branch.BranchLimitExceededException;
import com.vetos.platform.branch.BranchLimitPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateBranchUseCase {

    private final BranchRepository branchRepository;
    private final BranchLimitPort branchLimitPort;

    @Transactional
    public UUID execute(CreateBranchCommand command) {
        Integer maxBranches = branchLimitPort.maxBranchesFor(command.tenantId());
        if (maxBranches != null) {
            int currentCount = branchRepository.findByTenantId(command.tenantId()).size();
            if (currentCount >= maxBranches) {
                throw new BranchLimitExceededException(currentCount, maxBranches);
            }
        }
        Branch branch = Branch.create(command.tenantId(), command.name());
        String timezone = command.timezone() != null && !command.timezone().isBlank()
            ? command.timezone()
            : branch.getTimezone();
        branch.updateDetails(command.address(), command.city(), timezone, command.tarbilBranchCode());
        return branchRepository.save(branch).getId();
    }
}
