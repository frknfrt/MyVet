package com.vetos.modules.tenant.application;

import com.vetos.modules.tenant.application.dto.SubscriptionOverview;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.modules.tenant.domain.SubscriptionRepository;
import com.vetos.modules.tenant.domain.exception.SubscriptionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetSubscriptionOverviewUseCase {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public SubscriptionOverview execute(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
            .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));

        return new SubscriptionOverview(
            subscription.getPlanCode(), subscription.getStartedAt(), subscription.getRenewsAt(), subscription.getBillingStatus()
        );
    }
}
