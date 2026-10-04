package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import com.vetos.modules.platformadmin.domain.exception.CouponNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivateCouponUseCase {

    private final CouponRepository couponRepository;

    @Transactional
    public void execute(UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId).orElseThrow(() -> new CouponNotFoundException(couponId));
        coupon.activate();
        couponRepository.save(coupon);
    }
}
