package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.InviteStaffMemberCommand;
import com.vetos.modules.tenant.domain.InviteEmailPort;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffInviteStatus;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.modules.tenant.domain.exception.EmailAlreadyRegisteredConflictException;
import com.vetos.modules.tenant.domain.exception.StaffInviteAlreadyPendingConflictException;
import com.vetos.modules.tenant.domain.exception.TenantNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.observability.LogMasking;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteStaffMemberUseCase {

    private final StaffInviteRepository staffInviteRepository;
    private final StaffUserRepository staffUserRepository;
    private final TenantRepository tenantRepository;
    private final InviteEmailPort inviteEmailPort;
    private final AuditPort auditPort;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public UUID execute(InviteStaffMemberCommand command) {
        // staff_users.email ve bekleyen davetler GLOBAL kontrol edilir: iki sorgu da native, @TenantId filtresine takilmaz
        // (cagiranin kiracisiyla acilmis oturumda bile baska klinikteki kayit/davet yakalanir).
        boolean emailTaken = staffUserRepository.existsByEmail(command.email());
        if (emailTaken) {
            throw new EmailAlreadyRegisteredConflictException(command.email());
        }
        if (staffInviteRepository.existsByEmailAndStatus(command.email(), StaffInviteStatus.PENDING)) {
            throw new StaffInviteAlreadyPendingConflictException(command.email());
        }

        StaffInvite invite = staffInviteRepository.save(StaffInvite.create(
            command.tenantId(), command.branchId(), command.email(), command.fullName(),
            command.role(), command.invitedByStaffUserId()
        ));

        Tenant tenant = tenantRepository.findById(command.tenantId())
            .orElseThrow(() -> new TenantNotFoundException(command.tenantId()));
        String acceptUrl = frontendBaseUrl + "/davet/" + invite.getToken();
        inviteEmailPort.sendInvite(invite, tenant.getName(), acceptUrl);

        auditPort.record(AuditRecord.of(
            AuditAction.STAFF_INVITED, "STAFF_INVITE", invite.getId(), LogMasking.email(invite.getEmail()), "Rol: " + invite.getRole()
        ));
        return invite.getId();
    }
}
