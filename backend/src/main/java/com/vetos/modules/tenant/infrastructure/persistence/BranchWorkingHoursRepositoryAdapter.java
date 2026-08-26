package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.BranchWorkingHours;
import com.vetos.modules.tenant.domain.BranchWorkingHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class BranchWorkingHoursRepositoryAdapter implements BranchWorkingHoursRepository {

    private final BranchWorkingHoursJpaRepository jpaRepository;

    @Override
    public List<BranchWorkingHours> findByBranchId(UUID branchId) { return jpaRepository.findByBranchId(branchId); }

    @Override
    public void deleteByBranchId(UUID branchId) {
        jpaRepository.deleteByBranchId(branchId);
        jpaRepository.flush();
    }

    @Override
    public List<BranchWorkingHours> saveAll(List<BranchWorkingHours> entries) { return jpaRepository.saveAll(entries); }
}
