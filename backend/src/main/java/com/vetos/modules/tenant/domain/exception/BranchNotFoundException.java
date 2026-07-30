package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class BranchNotFoundException extends DomainException {
    public BranchNotFoundException(UUID id) {
        super("BRANCH_NOT_FOUND", "Sube bulunamadi: " + id);
    }
}
