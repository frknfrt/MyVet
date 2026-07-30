package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.dto.BranchOverview;

import java.util.UUID;

public record BranchOverviewResponse(
    UUID branchId,
    UUID tenantId,
    String tenantName,
    String branchName,
    String address,
    String city,
    String timezone,
    String tarbilBranchCode
) {
    public static BranchOverviewResponse from(BranchOverview overview) {
        return new BranchOverviewResponse(
            overview.branchId(), overview.tenantId(), overview.tenantName(), overview.branchName(),
            overview.address(), overview.city(), overview.timezone(), overview.tarbilBranchCode()
        );
    }
}
