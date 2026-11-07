package com.vetos.modules.dataimport.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Aktarim bu durumda bu islemi kabul etmiyor. GlobalExceptionHandler: sinif adi "ConflictException" ile bittigi icin 409. */
public class ImportStateConflictException extends DomainException {
    public ImportStateConflictException(String message) {
        super("IMPORT_STATE_CONFLICT", message);
    }
}
