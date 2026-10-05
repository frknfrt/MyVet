package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponDiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CouponResponse(
    UUID id,
    String code,
    CouponDiscountType discountType,
    BigDecimal discountValue,
    Integer maxRedemptions,
    int redemptionCount,
    LocalDate expiresAt,
    boolean active,
    boolean redeemable,
    Instant createdAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
            coupon.getId(),
            coupon.getCode(),
            coupon.getDiscountType(),
            coupon.getDiscountValue(),
            coupon.getMaxRedemptions(),
            coupon.getRedemptionCount(),
            coupon.getExpiresAt(),
            coupon.isActive(),
            coupon.isRedeemable(LocalDate.now()),
            coupon.getCreatedAt()
        );
    }
}
