package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.InviteStaffMemberCommand;
import com.vetos.modules.tenant.domain.InviteEmailPort;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffInviteStatus;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.modules.tenant.domain.exception.EmailAlreadyRegisteredConflictException;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Final review bulgusu 1: staff_users.email GLOBAL essiz (tenant-bazli
 * degil), ama StaffUser artik @TenantId'li oldugundan existsByEmail
 * sorgusu cagiranin context'inde calisirsa sessizce o kiraciyla
 * filtrelenir. CreateStaffUserUseCase'den farkli olarak burada asagi akista
 * bir DB kisiti YOK (StaffInvite olusturmak carpismaz) -- yani koprulme
 * olmadan bu hata sessizce davetiyenin gonderilmesine kadar ertelenir.
 * Bu testler kontrolun TenantContext.callInRootSession ile KOPRULENDIGINI
 * ve cagiranin context'inin hemen sonra geri yuklendigini dogrular.
 */
@ExtendWith(MockitoExtension.class)
class InviteStaffMemberUseCaseTest {

    @Mock private StaffInviteRepository staffInviteRepository;
    @Mock private StaffUserRepository staffUserRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private InviteEmailPort inviteEmailPort;

    @AfterEach
    void clearAmbientContext() {
        TenantContext.clear();
    }

    private InviteStaffMemberUseCase useCase() {
        InviteStaffMemberUseCase useCase =
            new InviteStaffMemberUseCase(staffInviteRepository, staffUserRepository, tenantRepository, inviteEmailPort);
        ReflectionTestUtils.setField(useCase, "frontendBaseUrl", "https://app.vetly.test");
        return useCase;
    }

    @Test
    void should_throwEmailAlreadyRegistered_when_emailBelongsToDifferentTenant() {
        UUID callerTenantId = UUID.randomUUID();
        TenantContext.set(callerTenantId);
        List<UUID> contextDuringCheck = new ArrayList<>();
        when(staffUserRepository.existsByEmail("cakisan@klinik.test")).thenAnswer(invocation -> {
            // Baska bir kiracida kayitli olan bir e-posta -- eger bu kontrol
            // (yanlislikla) cagiranin kendi context'inde calisirsa bu cakisma
            // GORULMEZ ve false doner, davetiye sessizce gonderilir.
            contextDuringCheck.add(TenantContext.currentOrNull());
            return true;
        });

        InviteStaffMemberCommand command = new InviteStaffMemberCommand(
            UUID.randomUUID(), UUID.randomUUID(), "cakisan@klinik.test", "Dr. Test", StaffRole.VET, UUID.randomUUID()
        );

        assertThatThrownBy(() -> useCase().execute(command))
            .isInstanceOf(EmailAlreadyRegisteredConflictException.class);

        // Kontrol root Session'da (context bos) calismis olmali.
        assertThat(contextDuringCheck).containsExactly((UUID) null);
        // Cagiranin orijinal context'i geri yuklenmis olmali, davetiye
        // e-posta portu HIC cagirilmamis olmali.
        assertThat(TenantContext.currentOrNull()).isEqualTo(callerTenantId);
        org.mockito.Mockito.verifyNoInteractions(inviteEmailPort);
        org.mockito.Mockito.verify(staffInviteRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void should_restoreCallersTenantContext_after_successfulInvite() {
        UUID callerTenantId = UUID.randomUUID();
        UUID targetTenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        TenantContext.set(callerTenantId);

        when(staffUserRepository.existsByEmail("yeni@klinik.test")).thenReturn(false);
        when(staffInviteRepository.existsByEmailAndStatus("yeni@klinik.test", StaffInviteStatus.PENDING)).thenReturn(false);
        when(staffInviteRepository.save(any(StaffInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Tenant tenant = Tenant.register("Test Klinik", null);
        ReflectionTestUtils.setField(tenant, "id", targetTenantId);
        when(tenantRepository.findById(targetTenantId)).thenReturn(Optional.of(tenant));

        InviteStaffMemberCommand command = new InviteStaffMemberCommand(
            targetTenantId, branchId, "yeni@klinik.test", "Dr. Test", StaffRole.VET, UUID.randomUUID()
        );

        useCase().execute(command);

        assertThat(TenantContext.currentOrNull()).isEqualTo(callerTenantId);
    }

    @Test
    void should_restoreCallersTenantContext_evenWhenEmailCheckThrows() {
        UUID callerTenantId = UUID.randomUUID();
        TenantContext.set(callerTenantId);
        when(staffUserRepository.existsByEmail("patlayan@klinik.test"))
            .thenThrow(new RuntimeException("db patladi"));

        InviteStaffMemberCommand command = new InviteStaffMemberCommand(
            UUID.randomUUID(), UUID.randomUUID(), "patlayan@klinik.test", "Dr. Test", StaffRole.VET, UUID.randomUUID()
        );

        assertThatThrownBy(() -> useCase().execute(command)).isInstanceOf(RuntimeException.class);

        assertThat(TenantContext.currentOrNull()).isEqualTo(callerTenantId);
    }
}
