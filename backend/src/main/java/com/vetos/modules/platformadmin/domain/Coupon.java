package com.vetos.modules.platformadmin.domain;

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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * vetly.com'daki self-servis kayit akisinda (bkz. InitiateSignupCheckoutUseCase)
 * ilk fatura tutarina uygulanan indirim kodu. Tek kullanimlik kampanyalar
 * (vb. potansiyel musteri takibinden gelen ozel teklifler) icin dusunuldu --
 * mevcut aylik faturalama donguyune (GenerateDueInvoicesUseCase) DAHIL DEGIL,
 * sadece kayit sirasindaki ilk odemeyi indirimli yapiyor.
 */
@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private CouponDiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Coupon create(
        String code, CouponDiscountType discountType, BigDecimal discountValue, Integer maxRedemptions, LocalDate expiresAt
    ) {
        Coupon coupon = new Coupon();
        coupon.code = code.trim().toUpperCase();
        coupon.discountType = discountType;
        coupon.discountValue = discountValue;
        coupon.maxRedemptions = maxRedemptions;
        coupon.redemptionCount = 0;
        coupon.expiresAt = expiresAt;
        coupon.active = true;
        coupon.createdAt = Instant.now();
        return coupon;
    }

    public boolean isRedeemable(LocalDate today) {
        if (!active) {
            return false;
        }
        if (expiresAt != null && today.isAfter(expiresAt)) {
            return false;
        }
        return maxRedemptions == null || redemptionCount < maxRedemptions;
    }

    /** amount uzerine indirimi uygular, sonucu 0'in altina dusurmez, 2 ondalige yuvarlar. */
    public BigDecimal applyTo(BigDecimal amount) {
        BigDecimal discounted = switch (discountType) {
            case PERCENTAGE -> amount.subtract(amount.multiply(discountValue).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
            case FIXED_AMOUNT -> amount.subtract(discountValue);
        };
        return discounted.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public void incrementRedemption() {
        this.redemptionCount++;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
