package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidTarbilMappingException extends DomainException {
    public InvalidTarbilMappingException(String reason) {
        super("INVALID_TARBIL_MAPPING", "Gecersiz TARBIL esletirmesi: " + reason);
    }
}
