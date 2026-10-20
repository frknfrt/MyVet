package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.ImpersonationSession;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.exception.ImpersonationTargetNotFoundException;
import com.vetos.modules.tenant.domain.ImpersonationTarget;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.audit.AuditActor;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartImpersonationUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;
    @Mock private AuditPort auditPort;

    @Test
    void should_generateTokenForTenantsAdminStaff_when_targetExists() {
        UUID tenantId = UUID.randomUUID();
        UUID platformAdminId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        ImpersonationTarget target = new ImpersonationTarget(staffUserId, branchId, "Ayse Yilmaz", StaffRole.ADMIN);
        when(tenantAdminPort.findImpersonationTarget(tenantId)).thenReturn(Optional.of(target));
        when(jwtTokenProvider.generateImpersonationToken(staffUserId, tenantId, List.of(branchId), "ADMIN", "admin@vetly.com.tr")).thenReturn("jwt-token-123");

        StartImpersonationUseCase useCase = new StartImpersonationUseCase(tenantAdminPort, jwtTokenProvider, recordAuditLogUseCase, auditPort);
        ImpersonationSession session = useCase.execute(tenantId, platformAdminId, "admin@vetly.com.tr");

        assertThat(session.token()).isEqualTo("jwt-token-123");
        assertThat(session.staffUserId()).isEqualTo(staffUserId);
        assertThat(session.tenantId()).isEqualTo(tenantId);
        assertThat(session.branchId()).isEqualTo(branchId);
        assertThat(session.fullName()).isEqualTo("Ayse Yilmaz");
        assertThat(session.role()).isEqualTo("ADMIN");
        verify(recordAuditLogUseCase).execute(
            eq(platformAdminId), eq("admin@vetly.com.tr"), eq(AuditAction.TENANT_IMPERSONATED), eq("TENANT"), eq(tenantId), any()
        );
    }

    @Test
    void should_issueImpersonationToken_and_recordClinicVisibleEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID platformAdminId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        ImpersonationTarget target = new ImpersonationTarget(staffUserId, branchId, "Ayse Yilmaz", StaffRole.ADMIN);
        when(tenantAdminPort.findImpersonationTarget(tenantId)).thenReturn(Optional.of(target));
        when(jwtTokenProvider.generateImpersonationToken(any(), any(), any(), any(), any())).thenReturn("jwt-token-123");

        StartImpersonationUseCase useCase = new StartImpersonationUseCase(tenantAdminPort, jwtTokenProvider, recordAuditLogUseCase, auditPort);
        useCase.execute(tenantId, platformAdminId, "destek@vetly.com.tr");

        verify(jwtTokenProvider).generateImpersonationToken(
            eq(target.staffUserId()), eq(tenantId), eq(List.of(target.branchId())), eq(target.role().name()), eq("destek@vetly.com.tr"));
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture(), eq(AuditActor.platformAdmin(platformAdminId, "destek@vetly.com.tr")));
        assertThat(captor.getValue().action()).isEqualTo(com.vetos.platform.audit.AuditAction.SUPPORT_IMPERSONATION_STARTED);
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().targetType()).isEqualTo("TENANT");
        assertThat(captor.getValue().summary()).isEqualTo("Yönetici hesabıyla");
    }

    @Test
    void should_throwNotFound_when_tenantHasNoAdminStaff() {
        UUID tenantId = UUID.randomUUID();
        when(tenantAdminPort.findImpersonationTarget(tenantId)).thenReturn(Optional.empty());

        StartImpersonationUseCase useCase = new StartImpersonationUseCase(tenantAdminPort, jwtTokenProvider, recordAuditLogUseCase, auditPort);

        assertThatThrownBy(() -> useCase.execute(tenantId, UUID.randomUUID(), "admin@vetly.com.tr"))
            .isInstanceOf(ImpersonationTargetNotFoundException.class);
        verifyNoInteractions(recordAuditLogUseCase);
        verifyNoInteractions(auditPort);
    }
}
