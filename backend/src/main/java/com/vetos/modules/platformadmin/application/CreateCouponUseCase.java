package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.CreateCouponCommand;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.Coupon;
import com.vetos.modules.platformadmin.domain.CouponRepository;
import com.vetos.modules.platformadmin.domain.exception.CouponCodeAlreadyExistsConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateCouponUseCase {

    private final CouponRepository couponRepository;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @Transactional
    public Coupon execute(CreateCouponCommand command, UUID platformAdminId, String platformAdminEmail) {
        String normalizedCode = command.code().trim().toUpperCase();
        if (couponRepository.findByCode(normalizedCode).isPresent()) {
            throw new CouponCodeAlreadyExistsConflictException(normalizedCode);
        }
        Coupon coupon = Coupon.create(
            normalizedCode, command.discountType(), command.discountValue(), command.maxRedemptions(), command.expiresAt()
        );
        Coupon saved = couponRepository.save(coupon);
        recordAuditLogUseCase.execute(
            platformAdminId, platformAdminEmail, AuditAction.COUPON_CREATED, "COUPON", saved.getId(), normalizedCode
        );
        return saved;
    }
}
