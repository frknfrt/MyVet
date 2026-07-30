package com.vetos.modules.patient.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class ConsentRecordNotFoundException extends DomainException {
    public ConsentRecordNotFoundException(UUID id) {
        super("CONSENT_RECORD_NOT_FOUND", "Onay kaydi bulunamadi: " + id);
    }
}
