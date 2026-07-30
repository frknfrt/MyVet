package com.vetos.modules.tenant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffUserRepository {
    StaffUser save(StaffUser staffUser);
    Optional<StaffUser> findById(UUID id);
    Optional<StaffUser> findByEmail(String email);
    boolean existsByEmail(String email);
    List<StaffUser> findByBranchId(UUID branchId);
}
