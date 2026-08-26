package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.dto.StaffInviteSummary;
import com.vetos.modules.tenant.domain.StaffInviteStatus;
import com.vetos.modules.tenant.domain.StaffRole;

import java.time.Instant;
import java.util.UUID;

public record StaffInviteResponse(
    UUID id, String email, String fullName, StaffRole role,
    StaffInviteStatus status, Instant createdAt, Instant expiresAt
) {
    public static StaffInviteResponse from(StaffInviteSummary s) {
        return new StaffInviteResponse(s.id(), s.email(), s.fullName(), s.role(), s.status(), s.createdAt(), s.expiresAt());
    }
}
