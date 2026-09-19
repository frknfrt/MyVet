package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.CreateStaffUserCommand;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.EmailAlreadyRegisteredConflictException;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateStaffUserUseCase {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UUID execute(CreateStaffUserCommand command) {
        if (staffUserRepository.existsByEmail(command.email())) {
            throw new EmailAlreadyRegisteredConflictException(command.email());
        }

        StaffUser staffUser = StaffUser.register(
            TenantContext.current(), command.branchId(), command.fullName(), command.email(),
            passwordEncoder.encode(command.password()), command.role()
        );
        staffUser.updateProfile(command.fullName(), command.phone(), command.licenseNumber(), command.specialty(), command.bio());

        return staffUserRepository.save(staffUser).getId();
    }
}
