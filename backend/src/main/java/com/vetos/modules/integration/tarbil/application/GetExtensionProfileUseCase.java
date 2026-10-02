package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionProfile;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetExtensionProfileUseCase {

    private final TenantLookupPort tenantLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public ExtensionProfile execute(UUID tenantId, UUID staffId) {
        return new ExtensionProfile(
            tenantLookupPort.findTenantName(tenantId).orElse("Klinik"),
            staffUserLookupPort.findSummaryById(staffId).fullName()
        );
    }
}
