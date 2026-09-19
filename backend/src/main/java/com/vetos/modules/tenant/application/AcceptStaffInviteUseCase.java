package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.AcceptStaffInviteCommand;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.EmailAlreadyRegisteredConflictException;
import com.vetos.modules.tenant.domain.exception.StaffInviteNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AcceptStaffInviteUseCase {

    private final StaffInviteRepository staffInviteRepository;
    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UUID execute(AcceptStaffInviteCommand command) {
        StaffInvite invite = staffInviteRepository.findByToken(command.token())
            .orElseThrow(() -> new StaffInviteNotFoundException(command.token()));
        invite.assertAcceptable();

        if (staffUserRepository.existsByEmail(invite.getEmail())) {
            throw new EmailAlreadyRegisteredConflictException(invite.getEmail());
        }

        StaffUser staffUser = StaffUser.register(
            invite.getTenantId(), invite.getBranchId(), invite.getFullName(), invite.getEmail(),
            passwordEncoder.encode(command.password()), invite.getRole()
        );
        staffUserRepository.save(staffUser);

        invite.accept();
        staffInviteRepository.save(invite);

        return staffUser.getId();
    }
}
