package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StaffUserJpaRepository extends JpaRepository<StaffUser, UUID> {
    Optional<StaffUser> findByEmail(String email);
    boolean existsByEmail(String email);
    List<StaffUser> findByBranchId(UUID branchId);
    long countByBranchIdIn(List<UUID> branchIds);
}
