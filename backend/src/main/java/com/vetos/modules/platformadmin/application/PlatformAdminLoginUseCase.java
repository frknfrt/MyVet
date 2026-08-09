package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.PlatformAdminAuthSession;
import com.vetos.modules.platformadmin.application.dto.PlatformAdminLoginCommand;
import com.vetos.modules.platformadmin.domain.PlatformAdminUser;
import com.vetos.modules.platformadmin.domain.PlatformAdminUserRepository;
import com.vetos.modules.platformadmin.domain.exception.PlatformAdminInvalidCredentialsException;
import com.vetos.platform.security.PlatformAdminJwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAdminLoginUseCase {

    private final PlatformAdminUserRepository platformAdminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAdminJwtTokenProvider platformAdminJwtTokenProvider;

    @Transactional(readOnly = true)
    public PlatformAdminAuthSession execute(PlatformAdminLoginCommand command) {
        PlatformAdminUser user = platformAdminUserRepository.findByEmail(command.email())
            .orElseThrow(PlatformAdminInvalidCredentialsException::new);

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new PlatformAdminInvalidCredentialsException();
        }

        String token = platformAdminJwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new PlatformAdminAuthSession(token, user.getId(), user.getEmail(), user.getFullName());
    }
}
