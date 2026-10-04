package com.vetos.modules.platformadmin.application.dto;

import com.vetos.modules.platformadmin.domain.CouponDiscountType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCouponCommand(
    String code,
    CouponDiscountType discountType,
    BigDecimal discountValue,
    Integer maxRedemptions,
    LocalDate expiresAt
) {}
