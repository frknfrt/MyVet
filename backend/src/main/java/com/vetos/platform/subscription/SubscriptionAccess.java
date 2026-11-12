package com.vetos.platform.subscription;

import java.time.LocalDate;

/** trialEndsAt / daysLeft yalnizca TRIAL ve TRIAL_EXPIRED icin doludur. */
public record SubscriptionAccess(SubscriptionAccessState state, LocalDate trialEndsAt, Integer daysLeft) {

    public static SubscriptionAccess of(SubscriptionAccessState state) {
        return new SubscriptionAccess(state, null, null);
    }

    public boolean isTrialExpired() {
        return state == SubscriptionAccessState.TRIAL_EXPIRED;
    }
}
