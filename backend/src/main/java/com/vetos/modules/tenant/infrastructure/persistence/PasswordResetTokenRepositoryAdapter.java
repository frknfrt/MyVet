package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.PasswordResetToken;
import com.vetos.modules.tenant.domain.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository jpaRepository;

    @Override
    public PasswordResetToken save(PasswordResetToken token) { return jpaRepository.save(token); }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) { return jpaRepository.findByTokenHash(tokenHash); }

    @Override
    public List<PasswordResetToken> findUnusedByStaffUserId(UUID staffUserId) {
        return jpaRepository.findByStaffUserIdAndUsedAtIsNull(staffUserId);
    }
}
