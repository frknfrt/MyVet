package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class CouponNotFoundException extends DomainException {
    public CouponNotFoundException(UUID id) {
        super("COUPON_NOT_FOUND", "Kupon bulunamadi: " + id);
    }
}
