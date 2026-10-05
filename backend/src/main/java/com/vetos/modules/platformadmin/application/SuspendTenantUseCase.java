package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantSuspensionReason;
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
    public void execute(UUID tenantId, TenantSuspensionReason reason, String note, UUID platformAdminId, String platformAdminEmail) {
        tenantAdminPort.suspend(tenantId, reason, note);
        String details = reason.name() + (note != null && !note.isBlank() ? " - " + note : "");
        recordAuditLogUseCase.execute(platformAdminId, platformAdminEmail, AuditAction.TENANT_SUSPENDED, "TENANT", tenantId, details);
    }
}
