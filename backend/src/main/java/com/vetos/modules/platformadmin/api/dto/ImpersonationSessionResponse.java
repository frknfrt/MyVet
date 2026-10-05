package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.application.dto.ImpersonationSession;

import java.util.UUID;

public record ImpersonationSessionResponse(String token, UUID staffUserId, UUID tenantId, UUID branchId, String fullName, String role) {
    public static ImpersonationSessionResponse from(ImpersonationSession s) {
        return new ImpersonationSessionResponse(s.token(), s.staffUserId(), s.tenantId(), s.branchId(), s.fullName(), s.role());
    }
}
