package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;

public class StaffInviteAlreadyPendingConflictException extends DomainException {
    public StaffInviteAlreadyPendingConflictException(String email) {
        super("STAFF_INVITE_ALREADY_PENDING", "Bu e-postaya zaten bekleyen bir davet var: " + email);
    }
}
