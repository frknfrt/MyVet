package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.exception.SubscriptionNotFoundException;
import com.vetos.modules.tenant.domain.exception.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TenantAdminPortAdapter implements TenantAdminPort {

    private final TenantJpaRepository tenantJpaRepository;
    private final SubscriptionJpaRepository subscriptionJpaRepository;
    private final BranchJpaRepository branchJpaRepository;
    private final StaffUserJpaRepository staffUserJpaRepository;

    @Override
    public List<TenantAdminOverview> listAll() {
        return tenantJpaRepository.findAll().stream().map(this::toOverview).toList();
    }

    @Override
    public TenantAdminOverview getOverview(UUID tenantId) {
        Tenant tenant = tenantJpaRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return toOverview(tenant);
    }

    @Override
    public void updateSubscription(UUID tenantId, String planCode, BillingStatus billingStatus, LocalDate renewsAt) {
        Subscription subscription = subscriptionJpaRepository.findByTenantId(tenantId)
            .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));
        subscription.changePlan(planCode, billingStatus, renewsAt);
        subscriptionJpaRepository.save(subscription);
    }

    @Override
    public void suspend(UUID tenantId) {
        Tenant tenant = tenantJpaRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        tenant.suspend();
        tenantJpaRepository.save(tenant);
    }

    @Override
    public void activate(UUID tenantId) {
        Tenant tenant = tenantJpaRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId));
        tenant.activate();
        tenantJpaRepository.save(tenant);
    }

    private TenantAdminOverview toOverview(Tenant tenant) {
        Subscription subscription = subscriptionJpaRepository.findByTenantId(tenant.getId())
            .orElseThrow(() -> new SubscriptionNotFoundException(tenant.getId()));
        List<Branch> branches = branchJpaRepository.findByTenantId(tenant.getId());
        List<UUID> branchIds = branches.stream().map(Branch::getId).toList();
        long staffCount = branchIds.isEmpty() ? 0 : staffUserJpaRepository.countByBranchIdIn(branchIds);

        return new TenantAdminOverview(
            tenant.getId(), tenant.getName(), tenant.getTaxNumber(), tenant.getStatus(), tenant.getCreatedAt(),
            subscription.getPlanCode(), subscription.getBillingStatus(), subscription.getStartedAt(), subscription.getRenewsAt(),
            branches.size(), (int) staffCount
        );
    }
}
