package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.dto.StaffUserOverview;

import java.time.Instant;
import java.util.UUID;

public record StaffUserResponse(
    UUID id,
    UUID branchId,
    String fullName,
    String email,
    String phone,
    String role,
    String licenseNumber,
    String specialty,
    String bio,
    boolean active,
    Instant createdAt
) {
    public static StaffUserResponse from(StaffUserOverview overview) {
        return new StaffUserResponse(
            overview.id(), overview.branchId(), overview.fullName(), overview.email(), overview.phone(),
            overview.role().name(), overview.licenseNumber(), overview.specialty(), overview.bio(),
            overview.active(), overview.createdAt()
        );
    }
}
