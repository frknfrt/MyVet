package com.vetos.modules.patient.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class PatientPhotoNotFoundException extends DomainException {
    public PatientPhotoNotFoundException(UUID patientId) {
        super("PATIENT_PHOTO_NOT_FOUND", "Hastanın profil fotoğrafı yok: " + patientId);
    }
}
