package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class VaccinationRecordNotFoundException extends DomainException {
    public VaccinationRecordNotFoundException(UUID id) {
        super("VACCINATION_RECORD_NOT_FOUND", "Asi kaydi bulunamadi: " + id);
    }
}
