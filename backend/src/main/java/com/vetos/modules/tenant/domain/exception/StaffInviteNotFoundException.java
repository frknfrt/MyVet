package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;

public class StaffInviteNotFoundException extends DomainException {
    public StaffInviteNotFoundException(Object id) {
        super("STAFF_INVITE_NOT_FOUND", "Davet bulunamadi: " + id);
    }
}
