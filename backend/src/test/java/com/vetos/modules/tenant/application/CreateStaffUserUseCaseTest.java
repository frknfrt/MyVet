package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.CreateStaffUserCommand;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.exception.EmailAlreadyRegisteredConflictException;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Final review bulgusu 1: staff_users.email GLOBAL essiz (tenant-bazli
 * degil), ama StaffUser artik @TenantId'li oldugundan existsByEmail
 * sorgusu cagiranin context'inde calisirsa sessizce o kiraciyla
 * filtrelenir. Bu testler, kontrolun TenantContext.callInRootSession ile
 * KOPRULENDIGINI (cagri aninda context null/root, cagridan sonra cagiranin
 * ORIJINAL kiracisi geri yuklenmis) ve baska kiracideki bir cakismanin
 * yakalandigini dogrular. Gercek Hibernate yok -- sadece TenantContext'in
 * existsByEmail cagrisi sirasindaki degeri yakalanir (AppointmentReminderSchedulerTest/
 * TenantAdminPortAdapterTest ile ayni desen).
 */
@ExtendWith(MockitoExtension.class)
class CreateStaffUserUseCaseTest {

    @Mock private StaffUserRepository staffUserRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearAmbientContext() {
        TenantContext.clear();
    }

    private CreateStaffUserUseCase useCase() {
        return new CreateStaffUserUseCase(staffUserRepository, passwordEncoder);
    }

    @Test
    void should_throwEmailAlreadyRegistered_when_emailBelongsToDifferentTenant() {
        UUID callerTenantId = UUID.randomUUID();
        TenantContext.set(callerTenantId);
        List<UUID> contextDuringCheck = new ArrayList<>();
        when(staffUserRepository.existsByEmail("cakisan@klinik.test")).thenAnswer(invocation -> {
            // Baska bir kiracida kayitli olan bir e-posta -- eger bu kontrol
            // (yanlislikla) cagiranin kendi context'inde calisirsa bu cakisma
            // GORULMEZ ve false doner.
            contextDuringCheck.add(TenantContext.currentOrNull());
            return true;
        });

        CreateStaffUserCommand command = new CreateStaffUserCommand(
            UUID.randomUUID(), "Dr. Test", "cakisan@klinik.test", "sifre123", StaffRole.VET,
            null, null, null, null
        );

        assertThatThrownBy(() -> useCase().execute(command))
            .isInstanceOf(EmailAlreadyRegisteredConflictException.class);

        // Kontrol root Session'da (context bos) calismis olmali.
        assertThat(contextDuringCheck).containsExactly((UUID) null);
        // Cagiranin orijinal context'i geri yuklenmis olmali.
        assertThat(TenantContext.currentOrNull()).isEqualTo(callerTenantId);
    }

    @Test
    void should_restoreCallersTenantContext_after_successfulCreation() {
        UUID callerTenantId = UUID.randomUUID();
        TenantContext.set(callerTenantId);
        UUID branchId = UUID.randomUUID();
        when(staffUserRepository.existsByEmail("yeni@klinik.test")).thenReturn(false);
        when(passwordEncoder.encode("sifre123")).thenReturn("hash");
        when(staffUserRepository.save(org.mockito.ArgumentMatchers.any(StaffUser.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateStaffUserCommand command = new CreateStaffUserCommand(
            branchId, "Dr. Test", "yeni@klinik.test", "sifre123", StaffRole.VET,
            null, null, null, null
        );

        // Basariyla tamamlanmali -- StaffUser.register icindeki
        // TenantContext.current() cagrisi context bos DEGILKEN calisir
        // (aksi halde IllegalStateException firlar), yani kontrol sonrasi
        // cagiranin GERCEK kiraci context'i geri yuklenmis olmali.
        useCase().execute(command);

        assertThat(TenantContext.currentOrNull()).isEqualTo(callerTenantId);
    }

    @Test
    void should_restoreCallersTenantContext_evenWhenEmailCheckThrows() {
        UUID callerTenantId = UUID.randomUUID();
        TenantContext.set(callerTenantId);
        when(staffUserRepository.existsByEmail("patlayan@klinik.test"))
            .thenThrow(new RuntimeException("db patladi"));

        CreateStaffUserCommand command = new CreateStaffUserCommand(
            UUID.randomUUID(), "Dr. Test", "patlayan@klinik.test", "sifre123", StaffRole.VET,
            null, null, null, null
        );

        assertThatThrownBy(() -> useCase().execute(command)).isInstanceOf(RuntimeException.class);

        assertThat(TenantContext.currentOrNull()).isEqualTo(callerTenantId);
    }
}
