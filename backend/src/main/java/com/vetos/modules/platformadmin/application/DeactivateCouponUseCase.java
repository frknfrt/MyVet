package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import com.vetos.modules.platformadmin.domain.exception.CouponNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeactivateCouponUseCase {

    private final CouponRepository couponRepository;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @Transactional
    public void execute(UUID couponId, UUID platformAdminId, String platformAdminEmail) {
        Coupon coupon = couponRepository.findById(couponId).orElseThrow(() -> new CouponNotFoundException(couponId));
        coupon.deactivate();
        couponRepository.save(coupon);
        recordAuditLogUseCase.execute(
            platformAdminId, platformAdminEmail, AuditAction.COUPON_DEACTIVATED, "COUPON", couponId, coupon.getCode()
        );
    }
}
