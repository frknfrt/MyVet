package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidPrescriptionItemException extends DomainException {
    public InvalidPrescriptionItemException() {
        super("INVALID_PRESCRIPTION_ITEM", "Stoktan verilen kalemde stok ürünü ve adet birlikte girilmeli");
    }
}
