package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    List<PasswordResetToken> findByStaffUserIdAndUsedAtIsNull(UUID staffUserId);
}
