package com.vetos.modules.platformadmin.application.dto;

import java.time.Instant;
import java.util.UUID;

public record RecentTenantSummary(UUID tenantId, String name, String planCode, Instant createdAt) {}
