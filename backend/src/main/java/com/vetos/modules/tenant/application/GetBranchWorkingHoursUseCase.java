package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.BranchWorkingHoursEntry;
import com.vetos.modules.tenant.domain.BranchWorkingHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetBranchWorkingHoursUseCase {

    private final BranchWorkingHoursRepository branchWorkingHoursRepository;

    @Transactional(readOnly = true)
    public List<BranchWorkingHoursEntry> execute(UUID branchId) {
        return branchWorkingHoursRepository.findByBranchId(branchId).stream()
            .map(entry -> new BranchWorkingHoursEntry(entry.getDayOfWeek(), entry.isClosed(), entry.getOpensAt(), entry.getClosesAt()))
            .toList();
    }
}
