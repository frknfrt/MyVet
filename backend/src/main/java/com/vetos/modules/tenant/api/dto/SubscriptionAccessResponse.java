package com.vetos.modules.tenant.api.dto;

import com.vetos.platform.subscription.SubscriptionAccess;

import java.time.LocalDate;

public record SubscriptionAccessResponse(String state, LocalDate trialEndsAt, Integer daysLeft) {
    public static SubscriptionAccessResponse from(SubscriptionAccess access) {
        return new SubscriptionAccessResponse(access.state().name(), access.trialEndsAt(), access.daysLeft());
    }
}
