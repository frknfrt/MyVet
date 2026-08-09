package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.CreateBranchCommand;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateBranchUseCase {

    private final BranchRepository branchRepository;

    @Transactional
    public UUID execute(CreateBranchCommand command) {
        Branch branch = Branch.create(command.tenantId(), command.name());
        String timezone = command.timezone() != null && !command.timezone().isBlank()
            ? command.timezone()
            : branch.getTimezone();
        branch.updateDetails(command.address(), command.city(), timezone, command.tarbilBranchCode());
        return branchRepository.save(branch).getId();
    }
}
