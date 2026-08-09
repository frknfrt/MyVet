package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;

public class SelfAccountManagementForbiddenException extends DomainException {
    public SelfAccountManagementForbiddenException() {
        super(
            "SELF_ACCOUNT_MANAGEMENT_FORBIDDEN",
            "Kendi hesabinizin rolunu ADMIN disina dusuremez veya kendinizi pasiflestiremezsiniz"
        );
    }
}
