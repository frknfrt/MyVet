package com.vetos.modules.platformadmin.application;

import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuspendTenantUseCase {

    private final TenantAdminPort tenantAdminPort;

    @Transactional
    public void execute(UUID tenantId) {
        tenantAdminPort.suspend(tenantId);
    }
}
