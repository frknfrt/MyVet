package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class StaffUserNotFoundException extends DomainException {
    public StaffUserNotFoundException(UUID id) {
        super("STAFF_USER_NOT_FOUND", "Personel bulunamadi: " + id);
    }
}
