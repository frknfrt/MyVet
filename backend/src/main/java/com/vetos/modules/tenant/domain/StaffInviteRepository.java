package com.vetos.modules.tenant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffInviteRepository {
    StaffInvite save(StaffInvite invite);
    Optional<StaffInvite> findById(UUID id);
    Optional<StaffInvite> findByToken(String token);
    boolean existsByEmailAndStatus(String email, StaffInviteStatus status);
    List<StaffInvite> findByTenantId(UUID tenantId);
}
