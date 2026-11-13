package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class SubscriptionNotOnTrialException extends DomainException {
    public SubscriptionNotOnTrialException() {
        super("SUBSCRIPTION_NOT_ON_TRIAL", "Bu işlem yalnızca deneme sürümündeki klinikler içindir.");
    }
}
