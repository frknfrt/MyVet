package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TenantLookupAdapter implements TenantLookupPort {

    private final TenantJpaRepository jpaRepository;

    @Override
    public List<UUID> findActiveTenantIds() {
        return jpaRepository.findByStatusIn(List.of(TenantStatus.ACTIVE, TenantStatus.TRIAL)).stream()
            .map(Tenant::getId)
            .toList();
    }
}
