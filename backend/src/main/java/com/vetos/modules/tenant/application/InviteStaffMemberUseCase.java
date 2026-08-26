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

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public UUID execute(InviteStaffMemberCommand command) {
        if (staffUserRepository.existsByEmail(command.email())) {
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

        return invite.getId();
    }
}
