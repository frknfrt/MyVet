package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.StaffUserOverview;
import com.vetos.modules.tenant.domain.StaffUser;
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
    public List<StaffUserOverview> execute(UUID branchId) {
        return staffUserRepository.findByBranchId(branchId).stream()
            .map(ListStaffUsersUseCase::toOverview)
            .toList();
    }

    private static StaffUserOverview toOverview(StaffUser staffUser) {
        return new StaffUserOverview(
            staffUser.getId(), staffUser.getBranchId(), staffUser.getFullName(), staffUser.getEmail(),
            staffUser.getPhone(), staffUser.getRole(), staffUser.getLicenseNumber(), staffUser.getSpecialty(),
            staffUser.getBio(), staffUser.isActive(), staffUser.getCreatedAt()
        );
    }
}
