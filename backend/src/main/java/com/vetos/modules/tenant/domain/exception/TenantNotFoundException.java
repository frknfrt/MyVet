package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class TenantNotFoundException extends DomainException {
    public TenantNotFoundException(UUID id) {
        super("TENANT_NOT_FOUND", "Kiraci bulunamadi: " + id);
    }
}
