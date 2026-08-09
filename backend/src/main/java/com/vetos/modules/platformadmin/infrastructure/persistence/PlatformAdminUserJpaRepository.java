package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.PlatformAdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PlatformAdminUserJpaRepository extends JpaRepository<PlatformAdminUser, UUID> {
    Optional<PlatformAdminUser> findByEmail(String email);
}
