package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.AuthSession;
import com.vetos.modules.tenant.application.dto.LoginCommand;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.InvalidCredentialsException;
import com.vetos.platform.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final StaffUserRepository staffUserRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public AuthSession execute(LoginCommand command) {
        StaffUser staffUser = staffUserRepository.findByEmail(command.email())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(command.password(), staffUser.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!staffUser.isActive()) {
            throw new InvalidCredentialsException();
        }

        Branch branch = branchRepository.findById(staffUser.getBranchId())
            .orElseThrow(InvalidCredentialsException::new);

        String token = jwtTokenProvider.generateToken(
            staffUser.getId(), branch.getTenantId(), List.of(branch.getId()), staffUser.getRole().name()
        );
        return new AuthSession(
            token, staffUser.getId(), branch.getTenantId(), branch.getId(), staffUser.getFullName(), staffUser.getRole()
        );
    }
}
