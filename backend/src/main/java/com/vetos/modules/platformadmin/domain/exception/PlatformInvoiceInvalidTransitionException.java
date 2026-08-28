package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.platform.exception.DomainException;

public class PlatformInvoiceInvalidTransitionException extends DomainException {
    public PlatformInvoiceInvalidTransitionException(PlatformInvoiceStatus from, PlatformInvoiceStatus to) {
        super("PLATFORM_INVOICE_INVALID_TRANSITION", "Fatura " + from + " durumundan " + to + " durumuna gecemez");
    }
}
