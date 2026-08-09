package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.UpdateStaffUserCommand;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.SelfAccountManagementForbiddenException;
import com.vetos.modules.tenant.domain.exception.StaffUserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateStaffUserUseCase {

    private final StaffUserRepository staffUserRepository;

    @Transactional
    public void execute(UpdateStaffUserCommand command) {
        boolean actingOnSelf = command.staffUserId().equals(command.actingStaffUserId());
        if (actingOnSelf && command.role() != StaffRole.ADMIN) {
            throw new SelfAccountManagementForbiddenException();
        }

        StaffUser staffUser = staffUserRepository.findById(command.staffUserId())
            .orElseThrow(() -> new StaffUserNotFoundException(command.staffUserId()));

        staffUser.updateProfile(command.fullName(), command.phone(), command.licenseNumber(), command.specialty(), command.bio());
        staffUser.changeRole(command.role());
        staffUserRepository.save(staffUser);
    }
}
