package com.vetos.modules.tenant.domain.exception;

import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.platform.exception.DomainException;

public class SubscriptionNotCancellableConflictException extends DomainException {
    public SubscriptionNotCancellableConflictException(BillingStatus currentStatus) {
        super("SUBSCRIPTION_NOT_CANCELLABLE", "Sadece aktif abonelikler iptal edilebilir (mevcut durum: " + currentStatus + ")");
    }
}
