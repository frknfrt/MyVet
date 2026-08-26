package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StaffInviteJpaRepository extends JpaRepository<StaffInvite, UUID> {
    Optional<StaffInvite> findByToken(String token);
    boolean existsByEmailAndStatus(String email, StaffInviteStatus status);
    List<StaffInvite> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
