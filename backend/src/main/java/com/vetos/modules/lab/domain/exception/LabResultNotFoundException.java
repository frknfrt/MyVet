package com.vetos.modules.lab.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class LabResultNotFoundException extends DomainException {
    public LabResultNotFoundException(UUID id) {
        super("LAB_RESULT_NOT_FOUND", "Laboratuvar sonucu bulunamadi: " + id);
    }
}
