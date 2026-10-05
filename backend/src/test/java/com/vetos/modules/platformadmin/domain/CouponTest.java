package com.vetos.modules.platformadmin.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CouponTest {

    @Test
    void should_uppercaseAndTrimCode_when_created() {
        Coupon coupon = Coupon.create(" welcome10 ", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);

        assertThat(coupon.getCode()).isEqualTo("WELCOME10");
        assertThat(coupon.isActive()).isTrue();
        assertThat(coupon.getRedemptionCount()).isZero();
    }

    @Test
    void should_applyPercentageDiscount_when_discountTypeIsPercentage() {
        Coupon coupon = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);

        assertThat(coupon.applyTo(new BigDecimal("2000.00"))).isEqualByComparingTo("1800.00");
    }

    @Test
    void should_applyFixedDiscount_when_discountTypeIsFixedAmount() {
        Coupon coupon = Coupon.create("INDIRIM250", CouponDiscountType.FIXED_AMOUNT, new BigDecimal("250.00"), null, null);

        assertThat(coupon.applyTo(new BigDecimal("2000.00"))).isEqualByComparingTo("1750.00");
    }

    @Test
    void should_neverGoBelowZero_when_fixedDiscountExceedsAmount() {
        Coupon coupon = Coupon.create("MEGA", CouponDiscountType.FIXED_AMOUNT, new BigDecimal("5000.00"), null, null);

        assertThat(coupon.applyTo(new BigDecimal("2000.00"))).isEqualByComparingTo("0.00");
    }

    @Test
    void should_beRedeemable_when_activeAndNotExpiredAndUnderLimit() {
        Coupon coupon = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), 5, LocalDate.now().plusDays(30));

        assertThat(coupon.isRedeemable(LocalDate.now())).isTrue();
    }

    @Test
    void should_notBeRedeemable_when_deactivated() {
        Coupon coupon = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);
        coupon.deactivate();

        assertThat(coupon.isRedeemable(LocalDate.now())).isFalse();
    }

    @Test
    void should_notBeRedeemable_when_expired() {
        Coupon coupon = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, LocalDate.now().minusDays(1));

        assertThat(coupon.isRedeemable(LocalDate.now())).isFalse();
    }

    @Test
    void should_notBeRedeemable_when_maxRedemptionsReached() {
        Coupon coupon = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), 1, null);
        coupon.incrementRedemption();

        assertThat(coupon.isRedeemable(LocalDate.now())).isFalse();
    }

    @Test
    void should_becomeRedeemableAgain_when_reactivated() {
        Coupon coupon = Coupon.create("WELCOME10", CouponDiscountType.PERCENTAGE, new BigDecimal("10"), null, null);
        coupon.deactivate();
        coupon.activate();

        assertThat(coupon.isRedeemable(LocalDate.now())).isTrue();
    }
}
