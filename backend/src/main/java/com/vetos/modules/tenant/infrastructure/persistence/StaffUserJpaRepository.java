package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StaffUserJpaRepository extends JpaRepository<StaffUser, UUID> {
    Optional<StaffUser> findByEmail(String email);
    /**
     * staff_users.email TUM kiracilarda essiz: kontrol de global olmali. Native sorgu @TenantId filtresinin disindadir
     * (cagiranin JWT kiracisiyla acilmis oturumda bile); callInRootSession islem icinde oturumu degistirmez.
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT EXISTS (SELECT 1 FROM staff_users WHERE email = :email)", nativeQuery = true)
    boolean existsByEmail(@org.springframework.data.repository.query.Param("email") String email);
    List<StaffUser> findByBranchId(UUID branchId);
    long countByBranchIdIn(List<UUID> branchIds);
    List<StaffUser> findByBranchIdInAndRole(List<UUID> branchIds, StaffRole role);
}
