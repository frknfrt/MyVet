package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreatePlatformTenantCommand;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatePlatformTenantUseCase {

    private final TenantAdminPort tenantAdminPort;

    @Transactional
    public UUID execute(CreatePlatformTenantCommand command) {
        return tenantAdminPort.createTenant(
            command.tenantName(), command.taxNumber(), command.branchName(),
            command.adminFullName(), command.adminEmail(), command.adminPassword()
        );
    }
}
