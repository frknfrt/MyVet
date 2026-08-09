package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.BranchWorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BranchWorkingHoursJpaRepository extends JpaRepository<BranchWorkingHours, UUID> {
    List<BranchWorkingHours> findByBranchId(UUID branchId);
    void deleteByBranchId(UUID branchId);
}
