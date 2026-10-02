package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidPairingCodeUnauthorizedException extends DomainException {
    public InvalidPairingCodeUnauthorizedException() {
        super("INVALID_PAIRING_CODE", "Eslestirme kodu gecersiz ya da suresi dolmus");
    }
}
