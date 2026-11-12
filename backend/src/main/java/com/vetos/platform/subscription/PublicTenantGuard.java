package com.vetos.platform.subscription;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kimlik dogrulamasiz klinik uclari (api/v1/public) TenantContext tasimaz, bu yuzden
 * SubscriptionGateInterceptor onlari goremez. Bu uclarin controller siniflari, istekteki
 * tenantId icin bu korumayi cagirir.
 */
@Component
public class PublicTenantGuard {

    private final SubscriptionAccessPort subscriptionAccess;

    public PublicTenantGuard(SubscriptionAccessPort subscriptionAccess) {
        this.subscriptionAccess = subscriptionAccess;
    }

    public void requireOperational(UUID tenantId) {
        if (tenantId != null && subscriptionAccess.accessOf(tenantId).isTrialExpired()) {
            throw new ClinicNotAvailableNotFoundException();
        }
    }
}
