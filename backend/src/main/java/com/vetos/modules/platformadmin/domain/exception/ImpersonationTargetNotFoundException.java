package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class ImpersonationTargetNotFoundException extends DomainException {
    public ImpersonationTargetNotFoundException(UUID tenantId) {
        super(
            "IMPERSONATION_TARGET_NOT_FOUND",
            "Bu kiracida impersonate edilebilecek aktif bir ADMIN kullanicisi bulunamadi: " + tenantId
        );
    }
}
