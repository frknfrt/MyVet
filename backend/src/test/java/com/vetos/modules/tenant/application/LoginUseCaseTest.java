package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.LoginCommand;
import com.vetos.modules.tenant.domain.*;
import com.vetos.modules.tenant.domain.exception.InvalidCredentialsException;
import com.vetos.modules.tenant.domain.exception.TenantSuspendedForbiddenException;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock private StaffUserRepository staffUserRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @Test
    void should_throwTenantSuspendedForbiddenException_when_tenantIsSuspended() {
        UUID branchId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        StaffUser staffUser = StaffUser.register(branchId, "Dr. Test", "test@example.com", "hash", StaffRole.VET);
        Branch branch = Branch.create(tenantId, "Merkez");
        Tenant tenant = Tenant.register("Test Klinik", "1234567890");
        tenant.suspend();

        when(staffUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        LoginUseCase useCase = new LoginUseCase(staffUserRepository, branchRepository, tenantRepository, passwordEncoder, jwtTokenProvider);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("test@example.com", "password123")))
            .isInstanceOf(TenantSuspendedForbiddenException.class);
    }

    @Test
    void should_succeed_when_tenantIsActive() {
        UUID branchId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        StaffUser staffUser = StaffUser.register(branchId, "Dr. Test", "test@example.com", "hash", StaffRole.VET);
        Branch branch = Branch.create(tenantId, "Merkez");
        ReflectionTestUtils.setField(branch, "id", branchId);
        Tenant tenant = Tenant.register("Test Klinik", "1234567890");
        tenant.activate();

        when(staffUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(jwtTokenProvider.generateToken(staffUser.getId(), tenantId, java.util.List.of(branch.getId()), "VET")).thenReturn("a-jwt-token");

        LoginUseCase useCase = new LoginUseCase(staffUserRepository, branchRepository, tenantRepository, passwordEncoder, jwtTokenProvider);
        var session = useCase.execute(new LoginCommand("test@example.com", "password123"));

        assertThat(session.token()).isEqualTo("a-jwt-token");
    }

    @Test
    void should_throwInvalidCredentials_when_passwordDoesNotMatch() {
        StaffUser staffUser = StaffUser.register(UUID.randomUUID(), "Dr. Test", "test@example.com", "hash", StaffRole.VET);
        when(staffUserRepository.findByEmail("test@example.com")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        LoginUseCase useCase = new LoginUseCase(staffUserRepository, branchRepository, tenantRepository, passwordEncoder, jwtTokenProvider);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("test@example.com", "wrong")))
            .isInstanceOf(InvalidCredentialsException.class);
    }
}
