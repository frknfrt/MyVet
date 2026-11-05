package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformBillingEmailPort;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.tenant.domain.BillableSubscription;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantSuspensionReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Klinik kendi istegiyle iptal ettiyse (CancelSubscriptionUseCase, Subscription
 * CANCELED), erisimi hemen KESMEYIZ -- zaten odenmis donem (renewsAt) sonuna
 * kadar kullanabilir. Bu donem gectiginde kiraciyi burada askiya aliyoruz.
 * FlagOverdueAndSuspendUseCase ile ayni desen (REQUIRES_NEW: disi transaction
 * PLATFORM_FATURALAMA_KILIDI'ni tutuyor, biri patlarsa digerleri etkilenmesin).
 */
@Service
@RequiredArgsConstructor
public class SuspendExpiredCancellationsUseCase {

    private final TenantAdminPort tenantAdminPort;
    private final PlatformBillingEmailPort platformBillingEmailPort;
    private final PlatformBillingSmsPort platformBillingSmsPort;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(LocalDate today) {
        for (BillableSubscription subscription : tenantAdminPort.listCanceledSubscriptionsDueOnOrBefore(today)) {
            tenantAdminPort.suspend(subscription.tenantId(), TenantSuspensionReason.SELF_CANCELLED, null);

            TenantAdminOverview overview = tenantAdminPort.getOverview(subscription.tenantId());
            tenantAdminPort.findBillingContactEmail(subscription.tenantId())
                .ifPresent(email -> platformBillingEmailPort.sendTenantSuspended(overview.name(), email));
            tenantAdminPort.findBillingContactPhone(subscription.tenantId())
                .ifPresent(phone -> platformBillingSmsPort.sendTenantSuspended(overview.name(), phone));
        }
    }
}
