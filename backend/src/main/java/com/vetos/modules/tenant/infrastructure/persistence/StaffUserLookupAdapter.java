package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffSummary;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import com.vetos.modules.tenant.domain.exception.StaffUserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class StaffUserLookupAdapter implements StaffUserLookupPort {

    private final StaffUserJpaRepository jpaRepository;

    @Override
    public StaffSummary findSummaryById(UUID staffUserId) {
        StaffUser staffUser = jpaRepository.findById(staffUserId)
            .orElseThrow(() -> new StaffUserNotFoundException(staffUserId));
        return new StaffSummary(staffUser.getId(), staffUser.getFullName(), staffUser.getRole(), staffUser.getBranchId());
    }
}
