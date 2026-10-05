package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListCouponsUseCase {

    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public List<Coupon> execute() {
        return couponRepository.findAllByOrderByCreatedAtDesc();
    }
}
