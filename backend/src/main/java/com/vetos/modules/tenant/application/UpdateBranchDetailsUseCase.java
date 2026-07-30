package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.UpdateBranchDetailsCommand;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.exception.BranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Klinik Kurulum Sihirbazi (Setup Wizard) suresince subenin adres/saat dilimi
 * gibi bilgilerini tamamlar.
 */
@Service
@RequiredArgsConstructor
public class UpdateBranchDetailsUseCase {

    private final BranchRepository branchRepository;

    @Transactional
    public void execute(UpdateBranchDetailsCommand command) {
        Branch branch = branchRepository.findById(command.branchId())
            .orElseThrow(() -> new BranchNotFoundException(command.branchId()));

        branch.updateDetails(command.address(), command.city(), command.timezone(), command.tarbilBranchCode());
        branchRepository.save(branch);
    }
}
