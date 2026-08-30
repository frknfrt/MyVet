package com.vetos.modules.tenant.domain;

import java.util.UUID;

public record TenantSignupResult(UUID tenantId, UUID branchId) {}
