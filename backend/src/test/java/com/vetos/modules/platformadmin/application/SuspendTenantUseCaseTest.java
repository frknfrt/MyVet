package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantSuspensionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SuspendTenantUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    private SuspendTenantUseCase useCase() {
        return new SuspendTenantUseCase(tenantAdminPort, recordAuditLogUseCase);
    }

    @Test
    void should_suspendTenantAndRecordAuditLog_withReasonAndNote() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        useCase().execute(tenantId, TenantSuspensionReason.PRICE, "Rakibe gecti", adminId, "admin@vetly.com.tr");

        verify(tenantAdminPort).suspend(tenantId, TenantSuspensionReason.PRICE, "Rakibe gecti");
        verify(recordAuditLogUseCase).execute(
            eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.TENANT_SUSPENDED), eq("TENANT"), eq(tenantId),
            eq("PRICE - Rakibe gecti")
        );
    }

    @Test
    void should_recordAuditLog_withoutNoteSuffix_when_noteIsBlank() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        useCase().execute(tenantId, TenantSuspensionReason.NOT_USING, null, adminId, "admin@vetly.com.tr");

        verify(recordAuditLogUseCase).execute(
            eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.TENANT_SUSPENDED), eq("TENANT"), eq(tenantId),
            eq("NOT_USING")
        );
    }
}
