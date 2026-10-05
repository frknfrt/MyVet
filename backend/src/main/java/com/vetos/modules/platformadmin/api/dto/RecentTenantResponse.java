package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.application.dto.RecentTenantSummary;

import java.time.Instant;
import java.util.UUID;

public record RecentTenantResponse(UUID tenantId, String name, String planCode, Instant createdAt) {
    public static RecentTenantResponse from(RecentTenantSummary s) {
        return new RecentTenantResponse(s.tenantId(), s.name(), s.planCode(), s.createdAt());
    }
}
