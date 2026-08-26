package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.dto.StaffInvitePublicSummary;
import com.vetos.modules.tenant.domain.StaffRole;

public record StaffInvitePublicResponse(String email, String fullName, StaffRole role, String tenantName) {
    public static StaffInvitePublicResponse from(StaffInvitePublicSummary s) {
        return new StaffInvitePublicResponse(s.email(), s.fullName(), s.role(), s.tenantName());
    }
}
