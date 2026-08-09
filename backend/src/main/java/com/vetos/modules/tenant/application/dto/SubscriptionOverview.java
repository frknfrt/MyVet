package com.vetos.modules.tenant.application.dto;

import com.vetos.modules.tenant.domain.BillingStatus;

import java.time.LocalDate;

public record SubscriptionOverview(String planCode, LocalDate startedAt, LocalDate renewsAt, BillingStatus billingStatus) {}
