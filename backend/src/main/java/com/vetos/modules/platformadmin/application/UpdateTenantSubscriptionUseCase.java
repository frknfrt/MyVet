package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.UpdateTenantSubscriptionCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateTenantSubscriptionUseCase {

    private final TenantAdminPort tenantAdminPort;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @Transactional
    public void execute(UpdateTenantSubscriptionCommand command, UUID platformAdminId, String platformAdminEmail) {
        tenantAdminPort.updateSubscription(command.tenantId(), command.planCode(), command.billingStatus(), command.renewsAt());
        recordAuditLogUseCase.execute(
            platformAdminId, platformAdminEmail, AuditAction.TENANT_SUBSCRIPTION_UPDATED, "TENANT", command.tenantId(),
            "planCode=" + command.planCode() + ", billingStatus=" + command.billingStatus() + ", renewsAt=" + command.renewsAt()
        );
    }
}
