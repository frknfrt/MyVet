package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class PlatformInvoiceNotFoundException extends DomainException {
    public PlatformInvoiceNotFoundException(UUID id) {
        super("PLATFORM_INVOICE_NOT_FOUND", "Fatura bulunamadi: " + id);
    }
}
