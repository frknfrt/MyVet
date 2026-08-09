package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.dto.SubscriptionOverview;

import java.time.LocalDate;

public record SubscriptionResponse(String planCode, LocalDate startedAt, LocalDate renewsAt, String billingStatus) {
    public static SubscriptionResponse from(SubscriptionOverview overview) {
        return new SubscriptionResponse(
            overview.planCode(), overview.startedAt(), overview.renewsAt(), overview.billingStatus().name()
        );
    }
}
