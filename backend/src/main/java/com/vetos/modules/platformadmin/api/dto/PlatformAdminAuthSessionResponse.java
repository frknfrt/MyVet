package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.application.dto.PlatformAdminAuthSession;

import java.util.UUID;

public record PlatformAdminAuthSessionResponse(
    String token,
    UUID platformAdminId,
    String email,
    String fullName
) {
    public static PlatformAdminAuthSessionResponse from(PlatformAdminAuthSession session) {
        return new PlatformAdminAuthSessionResponse(
            session.token(), session.platformAdminId(), session.email(), session.fullName()
        );
    }
}
