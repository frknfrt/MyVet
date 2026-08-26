package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.StaffInviteSummary;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffInviteStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListStaffInvitesUseCase {

    private final StaffInviteRepository staffInviteRepository;

    @Transactional(readOnly = true)
    public List<StaffInviteSummary> execute(UUID tenantId) {
        return staffInviteRepository.findByTenantId(tenantId).stream()
            .map(this::toSummary)
            .toList();
    }

    private StaffInviteSummary toSummary(StaffInvite invite) {
        StaffInviteStatus displayStatus = invite.isExpired() ? StaffInviteStatus.EXPIRED : invite.getStatus();
        return new StaffInviteSummary(
            invite.getId(), invite.getEmail(), invite.getFullName(), invite.getRole(),
            displayStatus, invite.getCreatedAt(), invite.getExpiresAt()
        );
    }
}
