package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.ChangePasswordCommand;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.InvalidCredentialsException;
import com.vetos.modules.tenant.domain.exception.StaffUserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangePasswordUseCase {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void execute(ChangePasswordCommand command) {
        StaffUser staffUser = staffUserRepository.findById(command.staffUserId())
            .orElseThrow(() -> new StaffUserNotFoundException(command.staffUserId()));

        if (!passwordEncoder.matches(command.currentPassword(), staffUser.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        staffUser.changePassword(passwordEncoder.encode(command.newPassword()));
        staffUserRepository.save(staffUser);
    }
}
