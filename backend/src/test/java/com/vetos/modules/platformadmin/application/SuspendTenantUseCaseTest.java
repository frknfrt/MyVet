package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SuspendTenantUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    @Test
    void should_suspendTenantAndRecordAuditLog() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        SuspendTenantUseCase useCase = new SuspendTenantUseCase(tenantAdminPort, recordAuditLogUseCase);

        useCase.execute(tenantId, adminId, "admin@vetly.com.tr");

        verify(tenantAdminPort).suspend(tenantId);
        verify(recordAuditLogUseCase).execute(eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.TENANT_SUSPENDED), eq("TENANT"), eq(tenantId), any());
    }
}
