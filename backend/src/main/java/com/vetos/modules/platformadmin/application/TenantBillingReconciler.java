package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Faturayla ilgili bir islem (odeme kaydi, void) bir kiracinin tek bir faturasini
 * cozume kavusturdugunda, kiracinin GENEL faturalama durumunu yeniden degerlendirir.
 * Diger faturalari hala ISSUED/OVERDUE ise kiraciyi rahatlatmaz -- boylece bir
 * faturanin odenmesi/void edilmesi, kiracinin bashka bir odenmemis faturasini
 * gormezden gelerek onu yanlislikla ACTIVE'e cekmez.
 */
@Component
@RequiredArgsConstructor
class TenantBillingReconciler {

    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final TenantAdminPort tenantAdminPort;

    /**
     * excludedInvoiceId is the invoice just paid/voided -- it may still carry its
     * pre-transition status in a stale in-memory reference elsewhere, so it is
     * excluded from the "still owes" check by id rather than relying on its status
     * having been persisted/refreshed already.
     */
    void reconcileAfterInvoiceResolved(UUID tenantId, UUID excludedInvoiceId) {
        boolean stillOwes = platformInvoiceRepository.findByTenantId(tenantId).stream()
            .filter(i -> !Objects.equals(i.getId(), excludedInvoiceId))
            .anyMatch(i -> i.getStatus() == PlatformInvoiceStatus.ISSUED || i.getStatus() == PlatformInvoiceStatus.OVERDUE);

        if (stillOwes) {
            return;
        }

        tenantAdminPort.updateBillingStatus(tenantId, BillingStatus.ACTIVE);
        TenantAdminOverview overview = tenantAdminPort.getOverview(tenantId);
        if (overview.status() == TenantStatus.SUSPENDED) {
            tenantAdminPort.activate(tenantId);
        }
    }
}
