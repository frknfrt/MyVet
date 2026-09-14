package com.vetos.modules.patient.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidNationalIdException extends DomainException {
    public InvalidNationalIdException() {
        super("INVALID_NATIONAL_ID", "Gecersiz TC kimlik numarasi");
    }
}
