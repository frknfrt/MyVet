package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.exception.BranchNotFoundException;
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

    private final BranchRepository branchRepository;
    private final BranchWorkingHoursRepository branchWorkingHoursRepository;

    @Transactional(readOnly = true)
    public List<BranchWorkingHoursEntry> execute(UUID branchId) {
        // Sube kiraci filtreli: baska klinigin subesi 404 (calisma saatleri tablosunda tenant_id yok).
        branchRepository.findById(branchId).orElseThrow(() -> new BranchNotFoundException(branchId));
        return branchWorkingHoursRepository.findByBranchId(branchId).stream()
            .map(entry -> new BranchWorkingHoursEntry(entry.getDayOfWeek(), entry.isClosed(), entry.getOpensAt(), entry.getClosesAt()))
            .toList();
    }
}
