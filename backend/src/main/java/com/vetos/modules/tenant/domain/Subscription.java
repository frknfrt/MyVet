package com.vetos.modules.tenant.domain;

import com.vetos.platform.subscription.ReservedPlanCodes;
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

    // Kiraci filtresi (spec 2026-09-17 S11): baska kiracinin kaydi kimlikle de okunamaz.

    @org.hibernate.annotations.TenantId

    @Column(name = "tenant_id", nullable = false, updatable = false)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false)
    private BillingPeriod billingPeriod = BillingPeriod.MONTHLY;

    /** startDate: klinigin takvim gunu (Europe/Istanbul) -- sunucu saat dilimine birakilmaz. */
    public static Subscription startTrial(UUID tenantId, LocalDate startDate, int trialDays) {
        Subscription subscription = new Subscription();
        subscription.tenantId = tenantId;
        subscription.planCode = ReservedPlanCodes.TRIAL;
        subscription.startedAt = startDate;
        subscription.renewsAt = startDate.plusDays(trialDays);
        subscription.billingStatus = BillingStatus.TRIAL;
        return subscription;
    }

    public static Subscription startPaid(UUID tenantId, String planCode, LocalDate renewsAt) {
        return startPaid(tenantId, planCode, renewsAt, BillingPeriod.MONTHLY);
    }

    public static Subscription startPaid(UUID tenantId, String planCode, LocalDate renewsAt, BillingPeriod billingPeriod) {
        Subscription subscription = new Subscription();
        subscription.tenantId = tenantId;
        subscription.planCode = planCode;
        subscription.startedAt = LocalDate.now();
        subscription.renewsAt = renewsAt;
        subscription.billingStatus = BillingStatus.ACTIVE;
        subscription.billingPeriod = java.util.Objects.requireNonNull(billingPeriod);
        return subscription;
    }

    public void changePlan(String planCode, BillingStatus billingStatus, LocalDate renewsAt) {
        this.planCode = planCode;
        this.billingStatus = billingStatus;
        this.renewsAt = renewsAt;
    }

    /**
     * Deneme, renewsAt gunu BOYUNCA gecerlidir; ertesi gun dolmus sayilir.
     * Dolmusluk saklanan bir durum degil, tarih karsilastirmasidir (spec 2026-11-13 S4.2).
     */
    public boolean isTrialExpired(LocalDate today) {
        return ReservedPlanCodes.TRIAL.equals(planCode) && renewsAt != null && renewsAt.isBefore(today);
    }

    /** Denemeden ucretli pakete gecis (odeme onaylaninca). */
    public void activatePaid(String planCode, LocalDate renewsAt, BillingPeriod billingPeriod) {
        this.planCode = planCode;
        this.renewsAt = renewsAt;
        this.billingPeriod = java.util.Objects.requireNonNull(billingPeriod);
        this.billingStatus = BillingStatus.ACTIVE;
    }

    public void advanceRenewal(LocalDate newRenewsAt) {
        this.renewsAt = newRenewsAt;
    }

    public void updateBillingStatus(BillingStatus billingStatus) {
        this.billingStatus = billingStatus;
    }

    /**
     * Klinigin kendi istegiyle iptal etmesi (self-servis). renewsAt'e KASITLI
     * olarak dokunulmaz: zaten odenmis donem sonuna kadar erisim surer,
     * SuspendExpiredCancellationsUseCase o tarih gecince kiraciyi askiya alir.
     */
    public void cancel() {
        this.billingStatus = BillingStatus.CANCELED;
    }

    /** Iptali geri alma -- sadece odenmis donem (renewsAt) bitmeden cagirilmasi beklenir. */
    public void reactivateCancellation() {
        this.billingStatus = BillingStatus.ACTIVE;
    }
}
