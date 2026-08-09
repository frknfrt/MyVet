package com.vetos.modules.platformadmin.application.dto;

import com.vetos.modules.tenant.domain.BillingStatus;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateTenantSubscriptionCommand(UUID tenantId, String planCode, BillingStatus billingStatus, LocalDate renewsAt) {}
