package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.tenant.domain.BillingPeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InitiateTrialUpgradeCheckoutRequest(
    @NotBlank @Size(max = 60) String planCode,
    BillingPeriod billingPeriod,
    @Size(max = 60) String couponCode
) {}
