package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TenantJpaRepository extends JpaRepository<Tenant, UUID> {
    List<Tenant> findByStatusIn(List<TenantStatus> statuses);
}
