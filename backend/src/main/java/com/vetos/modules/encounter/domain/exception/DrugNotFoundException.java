package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class DrugNotFoundException extends DomainException {
    public DrugNotFoundException(UUID id) {
        super("DRUG_NOT_FOUND", "Ilac bulunamadi: " + id);
    }
}
