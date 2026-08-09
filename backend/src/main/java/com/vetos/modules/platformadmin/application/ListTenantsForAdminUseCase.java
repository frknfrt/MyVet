package com.vetos.modules.platformadmin.application;

import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListTenantsForAdminUseCase {

    private final TenantAdminPort tenantAdminPort;

    @Transactional(readOnly = true)
    public List<TenantAdminOverview> execute() {
        return tenantAdminPort.listAll();
    }
}
