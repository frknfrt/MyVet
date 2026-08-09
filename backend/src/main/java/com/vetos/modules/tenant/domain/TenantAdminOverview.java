package com.vetos.modules.tenant.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TenantAdminOverview(
    UUID tenantId,
    String name,
    String taxNumber,
    TenantStatus status,
    Instant createdAt,
    String planCode,
    BillingStatus billingStatus,
    LocalDate startedAt,
    LocalDate renewsAt,
    int branchCount,
    int staffUserCount
) {}
