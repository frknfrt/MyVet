package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TarbilSubmissionNotFoundException extends DomainException {
    public TarbilSubmissionNotFoundException(UUID id) {
        super("TARBIL_SUBMISSION_NOT_FOUND", "TARBIL aktarim kaydi bulunamadi: " + id);
    }
}
