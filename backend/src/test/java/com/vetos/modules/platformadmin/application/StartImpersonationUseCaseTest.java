package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.ImpersonationSession;
import com.vetos.modules.platformadmin.domain.exception.ImpersonationTargetNotFoundException;
import com.vetos.modules.tenant.domain.ImpersonationTarget;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartImpersonationUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @Test
    void should_generateTokenForTenantsAdminStaff_when_targetExists() {
        UUID tenantId = UUID.randomUUID();
        UUID platformAdminId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        ImpersonationTarget target = new ImpersonationTarget(staffUserId, branchId, "Ayse Yilmaz", StaffRole.ADMIN);
        when(tenantAdminPort.findImpersonationTarget(tenantId)).thenReturn(Optional.of(target));
        when(jwtTokenProvider.generateToken(staffUserId, tenantId, List.of(branchId), "ADMIN")).thenReturn("jwt-token-123");

        StartImpersonationUseCase useCase = new StartImpersonationUseCase(tenantAdminPort, jwtTokenProvider);
        ImpersonationSession session = useCase.execute(tenantId, platformAdminId, "admin@vetly.com.tr");

        assertThat(session.token()).isEqualTo("jwt-token-123");
        assertThat(session.staffUserId()).isEqualTo(staffUserId);
        assertThat(session.tenantId()).isEqualTo(tenantId);
        assertThat(session.branchId()).isEqualTo(branchId);
        assertThat(session.fullName()).isEqualTo("Ayse Yilmaz");
        assertThat(session.role()).isEqualTo("ADMIN");
    }

    @Test
    void should_throwNotFound_when_tenantHasNoAdminStaff() {
        UUID tenantId = UUID.randomUUID();
        when(tenantAdminPort.findImpersonationTarget(tenantId)).thenReturn(Optional.empty());

        StartImpersonationUseCase useCase = new StartImpersonationUseCase(tenantAdminPort, jwtTokenProvider);

        assertThatThrownBy(() -> useCase.execute(tenantId, UUID.randomUUID(), "admin@vetly.com.tr"))
            .isInstanceOf(ImpersonationTargetNotFoundException.class);
    }
}
