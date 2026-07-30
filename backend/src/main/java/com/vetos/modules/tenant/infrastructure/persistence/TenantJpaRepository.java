package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TenantJpaRepository extends JpaRepository<Tenant, UUID> {
}
