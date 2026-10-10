package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Muayene taslak ya da TARBIL dusumu bekleyen ilac yok (spec 2026-10-09 S5.1) -> 409. */
public class TarbilNothingToDeductConflictException extends DomainException {
    public TarbilNothingToDeductConflictException(String message) {
        super("TARBIL_NOTHING_TO_DEDUCT", message);
    }
}
