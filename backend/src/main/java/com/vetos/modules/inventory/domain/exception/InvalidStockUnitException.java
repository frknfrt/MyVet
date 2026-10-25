package com.vetos.modules.inventory.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Elle birim ayari gecersiz: bilinmeyen birim ya da paketli birimde paket buyuklugu yok (spec 2026-10-24) -> 422. */
public class InvalidStockUnitException extends DomainException {
    public InvalidStockUnitException() {
        super("INVALID_STOCK_UNIT", "Kullanım birimi geçersiz: ml, g ve flakon için paket büyüklüğü girilmeli");
    }
}
