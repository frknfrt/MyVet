package com.vetos.modules.patient.domain.exception;

import com.vetos.platform.exception.DomainException;

public class UnsupportedPatientPhotoException extends DomainException {
    public UnsupportedPatientPhotoException() {
        super("UNSUPPORTED_PATIENT_PHOTO", "Profil fotoğrafı JPEG, PNG veya WEBP olmalı ve 2 MB'ı geçmemeli");
    }
}
