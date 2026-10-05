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

    /** Bekleyen davet kontrolu TUM kiracilarda (ayni e-postaya iki klinikten davet olmasin); native = filtresiz. */
    @org.springframework.data.jpa.repository.Query(
        value = "SELECT EXISTS (SELECT 1 FROM staff_invites WHERE email = :email AND status = :status)", nativeQuery = true)
    boolean existsByEmailAndStatusInAnyTenant(@org.springframework.data.repository.query.Param("email") String email,
                                              @org.springframework.data.repository.query.Param("status") String status);
    List<StaffInvite> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
