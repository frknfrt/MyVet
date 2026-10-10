package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;

public class PrescriptionNotCancellableConflictException extends DomainException {
    public PrescriptionNotCancellableConflictException() {
        super("PRESCRIPTION_NOT_CANCELLABLE", "Yalnız aktif reçete iptal edilebilir");
    }
}
