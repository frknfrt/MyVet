package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.EnabledFeaturesResponse;
import com.vetos.modules.tenant.api.dto.SubscriptionResponse;
import com.vetos.modules.tenant.application.GetEnabledFeaturesUseCase;
import com.vetos.modules.tenant.application.GetSubscriptionOverviewUseCase;
import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.quota.UsageSnapshot;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionsController {

    private final GetSubscriptionOverviewUseCase getSubscriptionOverviewUseCase;
    private final GetEnabledFeaturesUseCase getEnabledFeaturesUseCase;
    private final UsageQuotaPort usageQuotaPort;

    /** Klinik yoneticisi: bu ayki AI / mesaj kullanimi ve paket kotalari. */
    @GetMapping("/usage")
    @PreAuthorize("hasRole('ADMIN')")
    public UsageSnapshot usage() {
        return usageQuotaPort.currentUsage(TenantContext.current());
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionResponse getCurrent() {
        return SubscriptionResponse.from(getSubscriptionOverviewUseCase.execute(TenantContext.current()));
    }

    /** Tum personel: menu/rota gorunurlugu icin klinigin acik modulleri. */
    @GetMapping("/features")
    public EnabledFeaturesResponse getFeatures() {
        return EnabledFeaturesResponse.from(getEnabledFeaturesUseCase.execute(TenantContext.current()));
    }
}
