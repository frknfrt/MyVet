package com.vetos.modules.platformadmin.domain;

import java.util.Optional;
import java.util.UUID;

public interface TenantSignupRequestRepository {
    TenantSignupRequest save(TenantSignupRequest request);
    Optional<TenantSignupRequest> findById(UUID id);
}
