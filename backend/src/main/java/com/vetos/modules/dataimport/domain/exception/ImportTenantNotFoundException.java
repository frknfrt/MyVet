package com.vetos.modules.dataimport.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

/** GlobalExceptionHandler: sinif adi "NotFoundException" ile bittigi icin 404. */
public class ImportTenantNotFoundException extends DomainException {
    public ImportTenantNotFoundException(UUID tenantId) {
        super("TENANT_NOT_FOUND", "Klinik bulunamadı: " + tenantId);
    }
}
