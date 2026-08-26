package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class StaffInviteInvalidStateException extends DomainException {
    public StaffInviteInvalidStateException(UUID id, String reason) {
        super("STAFF_INVITE_INVALID_STATE", "Davet gecersiz (" + id + "): " + reason);
    }
}
