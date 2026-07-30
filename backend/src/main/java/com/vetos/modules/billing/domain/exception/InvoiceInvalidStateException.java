package com.vetos.modules.billing.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class InvoiceInvalidStateException extends DomainException {
    public InvoiceInvalidStateException(UUID invoiceId, String reason) {
        super("INVOICE_INVALID_STATE", "Fatura islemi gecersiz (" + invoiceId + "): " + reason);
    }
}
