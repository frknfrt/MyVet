package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.dto.AuthSession;

import java.util.UUID;

public record AuthSessionResponse(
    String token,
    UUID staffUserId,
    UUID tenantId,
    UUID branchId,
    String fullName,
    String role
) {
    public static AuthSessionResponse from(AuthSession session) {
        return new AuthSessionResponse(
            session.token(), session.staffUserId(), session.tenantId(),
            session.branchId(), session.fullName(), session.role().name()
        );
    }
}
