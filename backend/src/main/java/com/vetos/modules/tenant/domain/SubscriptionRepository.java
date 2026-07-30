package com.vetos.modules.tenant.domain;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {
    Subscription save(Subscription subscription);
    Optional<Subscription> findByTenantId(UUID tenantId);
}
