package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.tenant.domain.TenantAdminOverview;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TenantAdminOverviewResponse(
    UUID tenantId,
    String name,
    String taxNumber,
    String status,
    Instant createdAt,
    String planCode,
    String billingStatus,
    LocalDate startedAt,
    LocalDate renewsAt,
    int branchCount,
    int staffUserCount
) {
    public static TenantAdminOverviewResponse from(TenantAdminOverview overview) {
        return new TenantAdminOverviewResponse(
            overview.tenantId(), overview.name(), overview.taxNumber(), overview.status().name(), overview.createdAt(),
            overview.planCode(), overview.billingStatus().name(), overview.startedAt(), overview.renewsAt(),
            overview.branchCount(), overview.staffUserCount()
        );
    }
}
