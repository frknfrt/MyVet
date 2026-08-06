package com.vetos.modules.integration.efatura.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class EInvoiceSubmissionNotFoundException extends DomainException {
    public EInvoiceSubmissionNotFoundException(UUID id) {
        super("EFATURA_SUBMISSION_NOT_FOUND", "e-Fatura gonderim kaydi bulunamadi: " + id);
    }
}
