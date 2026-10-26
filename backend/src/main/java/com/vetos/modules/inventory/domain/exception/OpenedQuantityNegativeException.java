package com.vetos.modules.inventory.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Elle açık miktar düzeltmesi açık miktarı eksiye indiremez (değerlendirme I3, spec 2026-10-25 §4.4/14) -> 422. */
public class OpenedQuantityNegativeException extends DomainException {
    public OpenedQuantityNegativeException() {
        super("OPENED_QUANTITY_NEGATIVE", "Açık miktar eksiye inemez: düzeltme mevcut açık miktardan büyük");
    }
}
