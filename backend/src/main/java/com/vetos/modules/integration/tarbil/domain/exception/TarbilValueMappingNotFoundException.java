package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TarbilValueMappingNotFoundException extends DomainException {
    public TarbilValueMappingNotFoundException(UUID id) {
        super("TARBIL_VALUE_MAPPING_NOT_FOUND", "TARBIL esletirmesi bulunamadi: " + id);
    }
}
