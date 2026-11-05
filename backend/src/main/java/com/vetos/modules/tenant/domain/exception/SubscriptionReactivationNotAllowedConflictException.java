package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;

public class SubscriptionReactivationNotAllowedConflictException extends DomainException {
    public SubscriptionReactivationNotAllowedConflictException(String message) {
        super("SUBSCRIPTION_REACTIVATION_NOT_ALLOWED", message);
    }
}
