package com.vetos.modules.tenant.application.dto;

import java.util.UUID;

public record BranchOverview(
    UUID branchId,
    UUID tenantId,
    String tenantName,
    String branchName,
    String address,
    String city,
    String timezone,
    String tarbilBranchCode
) {}
