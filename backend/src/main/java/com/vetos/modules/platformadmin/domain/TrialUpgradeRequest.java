package com.vetos.modules.platformadmin.domain;

import com.vetos.modules.tenant.domain.BillingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Denemeden ucretli pakete gecis odemesi (spec 2026-11-13 S4.4). TenantSignupRequest ile ayni
 * rol: iyzico conversationId bu kaydin kimligidir. Fatura ODEME BASARILI OLUNCA kesilir --
 * yarim kalan bir odeme acik fatura birakmaz, gecikme/askiya alma zamanlayicisini tetiklemez.
 * Platform admin tarafinda tutuldugu icin (TenantSignupRequest gibi) kiraci filtresi tasimaz.
 */
@Entity
@Table(name = "trial_upgrade_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrialUpgradeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false)
    private BillingPeriod billingPeriod;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "charged_amount", nullable = false)
    private BigDecimal chargedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrialUpgradeRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static TrialUpgradeRequest create(
        UUID tenantId, String planCode, BillingPeriod billingPeriod, String couponCode, BigDecimal chargedAmount
    ) {
        TrialUpgradeRequest request = new TrialUpgradeRequest();
        request.tenantId = Objects.requireNonNull(tenantId);
        request.planCode = planCode;
        request.billingPeriod = Objects.requireNonNull(billingPeriod);
        request.couponCode = couponCode;
        request.chargedAmount = chargedAmount;
        request.status = TrialUpgradeRequestStatus.PENDING;
        request.createdAt = Instant.now();
        return request;
    }

    public boolean isCompleted() {
        return status == TrialUpgradeRequestStatus.COMPLETED;
    }

    /** Idempotent: tekrar gelen odeme callback'i icin exception firlatmaz. */
    public void complete() {
        this.status = TrialUpgradeRequestStatus.COMPLETED;
    }
}
