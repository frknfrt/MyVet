package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuspendTenantUseCase {

    private final TenantAdminPort tenantAdminPort;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @Transactional
    public void execute(UUID tenantId, UUID platformAdminId, String platformAdminEmail) {
        tenantAdminPort.suspend(tenantId);
        recordAuditLogUseCase.execute(platformAdminId, platformAdminEmail, AuditAction.TENANT_SUSPENDED, "TENANT", tenantId, null);
    }
}
