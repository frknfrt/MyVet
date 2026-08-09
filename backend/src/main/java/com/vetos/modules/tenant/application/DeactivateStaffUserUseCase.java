package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.SelfAccountManagementForbiddenException;
import com.vetos.modules.tenant.domain.exception.StaffUserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeactivateStaffUserUseCase {

    private final StaffUserRepository staffUserRepository;

    @Transactional
    public void execute(UUID staffUserId, UUID actingStaffUserId) {
        if (staffUserId.equals(actingStaffUserId)) {
            throw new SelfAccountManagementForbiddenException();
        }

        StaffUser staffUser = staffUserRepository.findById(staffUserId)
            .orElseThrow(() -> new StaffUserNotFoundException(staffUserId));
        staffUser.deactivate();
        staffUserRepository.save(staffUser);
    }
}
