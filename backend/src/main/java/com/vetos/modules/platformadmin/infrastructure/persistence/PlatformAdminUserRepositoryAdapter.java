package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformAdminUser;
import com.vetos.modules.platformadmin.domain.PlatformAdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class PlatformAdminUserRepositoryAdapter implements PlatformAdminUserRepository {

    private final PlatformAdminUserJpaRepository jpaRepository;

    @Override
    public PlatformAdminUser save(PlatformAdminUser user) { return jpaRepository.save(user); }

    @Override
    public Optional<PlatformAdminUser> findByEmail(String email) { return jpaRepository.findByEmail(email); }

    @Override
    public boolean existsAny() { return jpaRepository.count() > 0; }
}
