package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.StaffInvitePublicSummary;
import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.modules.tenant.domain.exception.StaffInviteNotFoundException;
import com.vetos.modules.tenant.domain.exception.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetStaffInviteByTokenUseCase {

    private final StaffInviteRepository staffInviteRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public StaffInvitePublicSummary execute(String token) {
        StaffInvite invite = staffInviteRepository.findByToken(token)
            .orElseThrow(() -> new StaffInviteNotFoundException(token));
        invite.assertAcceptable();

        Tenant tenant = tenantRepository.findById(invite.getTenantId())
            .orElseThrow(() -> new TenantNotFoundException(invite.getTenantId()));

        return new StaffInvitePublicSummary(invite.getEmail(), invite.getFullName(), invite.getRole(), tenant.getName());
    }
}
