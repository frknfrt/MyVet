package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class CouponCodeAlreadyExistsConflictException extends DomainException {
    public CouponCodeAlreadyExistsConflictException(String code) {
        super("COUPON_CODE_ALREADY_EXISTS", "Bu kupon kodu zaten kullaniliyor: " + code);
    }
}
