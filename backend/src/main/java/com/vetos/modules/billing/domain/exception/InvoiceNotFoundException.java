package com.vetos.modules.billing.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class InvoiceNotFoundException extends DomainException {
    public InvoiceNotFoundException(UUID id) {
        super("INVOICE_NOT_FOUND", "Fatura bulunamadi: " + id);
    }
}
