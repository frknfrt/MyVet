package com.vetos.modules.tenant.application.dto;

import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffInviteStatus;

import java.time.Instant;
import java.util.UUID;

public record StaffInviteSummary(
    UUID id, String email, String fullName, StaffRole role,
    StaffInviteStatus status, Instant createdAt, Instant expiresAt
) {}
