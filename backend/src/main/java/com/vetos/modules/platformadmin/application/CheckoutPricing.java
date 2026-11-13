package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.domain.exception.CouponNotRedeemableException;
import com.vetos.modules.platformadmin.domain.exception.PlanNotFoundException;
import com.vetos.modules.tenant.domain.BillingPeriod;
import com.vetos.platform.subscription.ReservedPlanCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Paket + donem + kupon icin tahsil edilecek tutari hesaplar. Kurallar
 * InitiateSignupCheckoutUseCase ile aynidir (aktif paket, Plan.priceFor, kupon indirimi);
 * ek olarak ayrilmis plan kodlari (TRIAL, INTERNAL) satin alinamaz.
 */
@Component
@RequiredArgsConstructor
public class CheckoutPricing {

    public record PricedCheckout(Plan plan, BillingPeriod billingPeriod, BigDecimal chargedAmount, String normalizedCouponCode) {}

    private final PlanRepository planRepository;
    private final CouponRepository couponRepository;

    public PricedCheckout price(String planCode, BillingPeriod billingPeriod, String couponCode, LocalDate today) {
        BillingPeriod period = billingPeriod == null ? BillingPeriod.MONTHLY : billingPeriod;
        if (planCode == null || ReservedPlanCodes.isReserved(planCode)) {
            throw new PlanNotFoundException(String.valueOf(planCode));
        }
        Plan plan = planRepository.findByCode(planCode)
            .filter(Plan::isActive)
            .orElseThrow(() -> new PlanNotFoundException(planCode));

        BigDecimal chargedAmount = plan.priceFor(period);
        String normalizedCouponCode = null;
        if (couponCode != null && !couponCode.isBlank()) {
            String normalized = couponCode.trim().toUpperCase();
            Coupon coupon = couponRepository.findByCode(normalized)
                .filter(c -> c.isRedeemable(today))
                .orElseThrow(() -> new CouponNotRedeemableException(normalized));
            chargedAmount = coupon.applyTo(chargedAmount);
            normalizedCouponCode = normalized;
        }
        return new PricedCheckout(plan, period, chargedAmount, normalizedCouponCode);
    }
}
