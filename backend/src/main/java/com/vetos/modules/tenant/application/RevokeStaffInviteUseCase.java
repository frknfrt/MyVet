package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.exception.StaffInviteNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevokeStaffInviteUseCase {

    private final StaffInviteRepository staffInviteRepository;

    @Transactional
    public void execute(UUID id) {
        StaffInvite invite = staffInviteRepository.findById(id)
            .orElseThrow(() -> new StaffInviteNotFoundException(id));
        invite.revoke();
        staffInviteRepository.save(invite);
    }
}
