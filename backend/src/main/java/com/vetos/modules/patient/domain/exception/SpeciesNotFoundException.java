package com.vetos.modules.patient.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class SpeciesNotFoundException extends DomainException {
    public SpeciesNotFoundException(UUID id) {
        super("SPECIES_NOT_FOUND", "Tur bulunamadi: " + id);
    }
}
