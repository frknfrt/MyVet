package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TarbilExtensionTokenNotFoundException extends DomainException {
    public TarbilExtensionTokenNotFoundException(UUID id) {
        super("TARBIL_EXTENSION_TOKEN_NOT_FOUND", "Eklenti baglantisi bulunamadi: " + id);
    }
}
