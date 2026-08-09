package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.SetBranchWorkingHoursCommand;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.BranchWorkingHours;
import com.vetos.modules.tenant.domain.BranchWorkingHoursRepository;
import com.vetos.modules.tenant.domain.exception.BranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetBranchWorkingHoursUseCase {

    private final BranchRepository branchRepository;
    private final BranchWorkingHoursRepository branchWorkingHoursRepository;

    @Transactional
    public void execute(SetBranchWorkingHoursCommand command) {
        branchRepository.findById(command.branchId())
            .orElseThrow(() -> new BranchNotFoundException(command.branchId()));

        branchWorkingHoursRepository.deleteByBranchId(command.branchId());
        var entries = command.entries().stream()
            .map(entry -> entry.closed()
                ? BranchWorkingHours.closedDay(command.branchId(), entry.dayOfWeek())
                : BranchWorkingHours.open(command.branchId(), entry.dayOfWeek(), entry.opensAt(), entry.closesAt()))
            .toList();
        branchWorkingHoursRepository.saveAll(entries);
    }
}
