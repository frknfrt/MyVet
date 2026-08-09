package com.vetos.modules.tenant.domain;

import java.util.List;
import java.util.UUID;

public interface BranchWorkingHoursRepository {
    List<BranchWorkingHours> findByBranchId(UUID branchId);
    void deleteByBranchId(UUID branchId);
    List<BranchWorkingHours> saveAll(List<BranchWorkingHours> entries);
}
