package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreatePlatformTenantCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePlatformTenantUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    @Test
    void should_createTenantAndRecordAuditLog() {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(tenantAdminPort.createTenant("Mutlu Pati", "123", "Merkez", "Adres", "Istanbul", "Ayse", "ayse@example.com", "sifre123"))
            .thenReturn(tenantId);
        CreatePlatformTenantUseCase useCase = new CreatePlatformTenantUseCase(tenantAdminPort, recordAuditLogUseCase);

        UUID result = useCase.execute(new CreatePlatformTenantCommand(
            "Mutlu Pati", "123", "Merkez", "Adres", "Istanbul", "Ayse", "ayse@example.com", "sifre123"
        ), adminId, "admin@vetly.com.tr");

        assertThat(result).isEqualTo(tenantId);
        verify(recordAuditLogUseCase).execute(eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.TENANT_CREATED), eq("TENANT"), eq(tenantId), any());
    }
}
