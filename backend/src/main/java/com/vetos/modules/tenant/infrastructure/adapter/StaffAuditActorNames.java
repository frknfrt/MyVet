package com.vetos.modules.tenant.infrastructure.adapter;

import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.platform.audit.AuditActorNames;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Denetim kaydina yazma anindaki personel adi. Cagiranin kiraci oturumunda calisir (ayni kiraci). */
@Component
class StaffAuditActorNames implements AuditActorNames {

    private final StaffUserRepository staffUserRepository;

    StaffAuditActorNames(StaffUserRepository staffUserRepository) {
        this.staffUserRepository = staffUserRepository;
    }

    @Override
    public String staffName(UUID tenantId, UUID staffUserId) {
        return staffUserRepository.findById(staffUserId)
            .filter(s -> tenantId.equals(s.getTenantId()))
            .map(StaffUser::getFullName)
            .orElse(null);
    }
}
