package com.vetos.modules.platformadmin.application;

import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTenantAdminOverviewUseCase {

    private final TenantAdminPort tenantAdminPort;

    @Transactional(readOnly = true)
    public TenantAdminOverview execute(UUID tenantId) {
        return tenantAdminPort.getOverview(tenantId);
    }
}
