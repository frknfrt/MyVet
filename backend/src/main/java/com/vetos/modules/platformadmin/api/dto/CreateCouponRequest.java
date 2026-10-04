package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.CouponDiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCouponRequest(
    @NotBlank String code,
    @NotNull CouponDiscountType discountType,
    @NotNull @DecimalMin("0.01") BigDecimal discountValue,
    @Min(1) Integer maxRedemptions,
    LocalDate expiresAt
) {}
