package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class CouponNotRedeemableException extends DomainException {
    public CouponNotRedeemableException(String code) {
        super("COUPON_NOT_REDEEMABLE", "Kupon kodu gecersiz, suresi dolmus veya kullanim limitine ulasmis: " + code);
    }
}
