package com.vetos.modules.tenant.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class SubscriptionNotFoundException extends DomainException {
    public SubscriptionNotFoundException(UUID tenantId) {
        super("SUBSCRIPTION_NOT_FOUND", "Abonelik bulunamadi: " + tenantId);
    }
}
