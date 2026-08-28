package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TenantSuspendedForbiddenException extends DomainException {
    public TenantSuspendedForbiddenException() {
        super(
            "TENANT_SUSPENDED",
            "Kliniginizin aboneligi askiya alinmis. Odeme kaydedildikten sonra hesabiniz otomatik olarak yeniden aktif olur."
        );
    }
}
