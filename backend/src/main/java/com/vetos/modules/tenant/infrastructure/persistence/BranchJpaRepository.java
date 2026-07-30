package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BranchJpaRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByTenantId(UUID tenantId);
}
