package com.vetos.modules.tenant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    List<PasswordResetToken> findUnusedByStaffUserId(UUID staffUserId);
}
