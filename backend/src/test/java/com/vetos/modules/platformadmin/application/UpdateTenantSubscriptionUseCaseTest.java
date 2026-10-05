package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.UpdateTenantSubscriptionCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpdateTenantSubscriptionUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    @Test
    void should_updateSubscriptionAndRecordAuditLog() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        LocalDate renewsAt = LocalDate.of(2026, 10, 1);
        UpdateTenantSubscriptionUseCase useCase = new UpdateTenantSubscriptionUseCase(tenantAdminPort, recordAuditLogUseCase);

        useCase.execute(new UpdateTenantSubscriptionCommand(tenantId, "PRO", BillingStatus.ACTIVE, renewsAt), adminId, "admin@vetly.com.tr");

        verify(tenantAdminPort).updateSubscription(tenantId, "PRO", BillingStatus.ACTIVE, renewsAt);
        verify(recordAuditLogUseCase).execute(eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.TENANT_SUBSCRIPTION_UPDATED), eq("TENANT"), eq(tenantId), any());
    }
}
