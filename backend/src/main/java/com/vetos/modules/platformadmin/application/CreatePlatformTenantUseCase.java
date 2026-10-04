package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreatePlatformTenantCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatePlatformTenantUseCase {

    private final TenantAdminPort tenantAdminPort;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @Transactional
    public UUID execute(CreatePlatformTenantCommand command, UUID platformAdminId, String platformAdminEmail) {
        UUID tenantId = tenantAdminPort.createTenant(
            command.tenantName(), command.taxNumber(), command.branchName(), command.address(), command.city(),
            command.adminFullName(), command.adminEmail(), command.adminPassword()
        );
        recordAuditLogUseCase.execute(
            platformAdminId, platformAdminEmail, AuditAction.TENANT_CREATED, "TENANT", tenantId, command.tenantName()
        );
        return tenantId;
    }
}
