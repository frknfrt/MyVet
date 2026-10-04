package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.tenant.domain.TenantSuspensionReason;
import jakarta.validation.constraints.NotNull;

public record SuspendTenantRequest(
    @NotNull TenantSuspensionReason reason,
    String note
) {}
