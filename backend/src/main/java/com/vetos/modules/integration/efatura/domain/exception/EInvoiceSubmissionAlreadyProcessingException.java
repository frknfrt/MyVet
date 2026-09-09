package com.vetos.modules.integration.efatura.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

/**
 * Saglayici (faturaentegrator) istegi zaten kabul etti, GIB resmilesme
 * callback'i bekleniyor -- bu asamada tekrar gonderim yapmak saglayici
 * tarafinda mukerrer fatura olusturur. Kullanici callback'i beklemeli.
 */
public class EInvoiceSubmissionAlreadyProcessingException extends DomainException {
    public EInvoiceSubmissionAlreadyProcessingException(UUID id) {
        super(
            "EFATURA_SUBMISSION_ALREADY_PROCESSING",
            "Bu gonderim zaten saglayiciya iletildi, GIB resmilesme bildirimi bekleniyor: " + id
        );
    }
}
