package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.SubscriptionResponse;
import com.vetos.modules.tenant.application.GetSubscriptionOverviewUseCase;
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

    @GetMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    public SubscriptionResponse getCurrent() {
        return SubscriptionResponse.from(getSubscriptionOverviewUseCase.execute(TenantContext.current()));
    }
}
