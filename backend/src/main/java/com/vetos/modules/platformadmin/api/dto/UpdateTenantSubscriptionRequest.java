package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.tenant.domain.BillingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateTenantSubscriptionRequest(
    @NotBlank String planCode,
    @NotNull BillingStatus billingStatus,
    LocalDate renewsAt
) {}
