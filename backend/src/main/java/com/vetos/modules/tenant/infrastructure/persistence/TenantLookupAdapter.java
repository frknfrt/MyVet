package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.platform.subscription.SubscriptionAccessPort;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TenantLookupAdapter implements TenantLookupPort {

    private final TenantJpaRepository jpaRepository;
    private final SubscriptionAccessPort subscriptionAccessPort;

    @Override
    public List<UUID> findActiveTenantIds() {
        // Suresi dolmus deneme askida degildir (giris acik kalir) ama aktif de sayilmaz:
        // hatirlatma zamanlayicilari ve eklenti anahtari bu kliniklerde calismaz.
        return jpaRepository.findByStatusIn(List.of(TenantStatus.ACTIVE, TenantStatus.TRIAL)).stream()
            .map(Tenant::getId)
            .filter(id -> !subscriptionAccessPort.accessOf(id).isTrialExpired())
            .toList();
    }

    @Override
    public boolean isOperational(UUID tenantId) {
        return jpaRepository.findById(tenantId).map(t -> t.getStatus() != TenantStatus.SUSPENDED).orElse(false)
            && !subscriptionAccessPort.accessOf(tenantId).isTrialExpired();
    }

    @Override
    public Optional<String> findTenantName(UUID tenantId) {
        return jpaRepository.findById(tenantId).map(Tenant::getName);
    }
}
