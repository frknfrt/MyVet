package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.UpdateTenantSubscriptionCommand;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateTenantSubscriptionUseCase {

    private final TenantAdminPort tenantAdminPort;

    @Transactional
    public void execute(UpdateTenantSubscriptionCommand command) {
        tenantAdminPort.updateSubscription(command.tenantId(), command.planCode(), command.billingStatus(), command.renewsAt());
    }
}
