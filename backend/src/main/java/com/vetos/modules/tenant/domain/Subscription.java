package com.vetos.modules.tenant.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(name = "started_at", nullable = false)
    private LocalDate startedAt;

    @Column(name = "renews_at")
    private LocalDate renewsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", nullable = false)
    private BillingStatus billingStatus;

    public static Subscription startTrial(UUID tenantId) {
        Subscription subscription = new Subscription();
        subscription.tenantId = tenantId;
        subscription.planCode = "TRIAL";
        subscription.startedAt = LocalDate.now();
        subscription.renewsAt = LocalDate.now().plusDays(14);
        subscription.billingStatus = BillingStatus.TRIAL;
        return subscription;
    }

    public void changePlan(String planCode, BillingStatus billingStatus, LocalDate renewsAt) {
        this.planCode = planCode;
        this.billingStatus = billingStatus;
        this.renewsAt = renewsAt;
    }
}
