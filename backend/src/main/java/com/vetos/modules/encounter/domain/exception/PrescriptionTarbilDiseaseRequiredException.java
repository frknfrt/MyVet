package com.vetos.modules.encounter.domain.exception;

import com.vetos.platform.exception.DomainException;

public class PrescriptionTarbilDiseaseRequiredException extends DomainException {
    public PrescriptionTarbilDiseaseRequiredException() {
        super("PRESCRIPTION_TARBIL_DISEASE_REQUIRED", "Stoktan verilen ilaç için TARBİL teşhisi seçilmeli");
    }
}
