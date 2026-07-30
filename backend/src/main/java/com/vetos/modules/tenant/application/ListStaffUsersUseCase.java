package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.domain.StaffSummary;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListStaffUsersUseCase {

    private final StaffUserRepository staffUserRepository;

    @Transactional(readOnly = true)
    public List<StaffSummary> execute(UUID branchId) {
        return staffUserRepository.findByBranchId(branchId).stream()
            .map(staffUser -> new StaffSummary(staffUser.getId(), staffUser.getFullName(), staffUser.getRole(), staffUser.getBranchId()))
            .toList();
    }
}
