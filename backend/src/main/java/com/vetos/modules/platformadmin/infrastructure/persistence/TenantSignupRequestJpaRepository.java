package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TenantSignupRequestJpaRepository extends JpaRepository<TenantSignupRequest, UUID> {
}
