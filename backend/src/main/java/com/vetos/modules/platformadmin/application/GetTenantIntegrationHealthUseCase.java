package com.vetos.modules.platformadmin.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceHealthPort;
import com.vetos.modules.integration.tarbil.domain.TarbilHealthPort;
import com.vetos.modules.platformadmin.application.dto.TenantIntegrationHealth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Tenant Detayi sayfasi -- bu kiracinin e-Fatura ve TARBIL entegrasyonlarinda
 * suan kac basarisiz kayit biriktigini gosterir. Global Sistem Sagligi
 * panelinin (bkz. ListPlatformSystemHealthUseCase) aksine tek bir kiraciya
 * odaklidir.
 */
@Service
@RequiredArgsConstructor
public class GetTenantIntegrationHealthUseCase {

    private final EInvoiceHealthPort eInvoiceHealthPort;
    private final TarbilHealthPort tarbilHealthPort;

    public TenantIntegrationHealth execute(UUID tenantId) {
        return new TenantIntegrationHealth(
            eInvoiceHealthPort.countFailedForTenant(tenantId),
            tarbilHealthPort.countFailedForTenant(tenantId)
        );
    }
}
