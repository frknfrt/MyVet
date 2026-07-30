package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.modules.tenant.domain.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository jpaRepository;

    @Override
    public Subscription save(Subscription subscription) { return jpaRepository.save(subscription); }

    @Override
    public Optional<Subscription> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }
}
