package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Recetedeki TARBIL teshisi katalogda yok (review 2026-10-10) -> 422. */
public class InvalidTarbilDiseaseException extends DomainException {
    public InvalidTarbilDiseaseException() {
        super("INVALID_TARBIL_DISEASE", "TARBİL teşhisi bulunamadı");
    }
}
