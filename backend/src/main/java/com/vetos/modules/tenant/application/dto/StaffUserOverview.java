package com.vetos.modules.tenant.application.dto;

import com.vetos.modules.tenant.domain.StaffRole;

import java.time.Instant;
import java.util.UUID;

public record StaffUserOverview(
    UUID id,
    UUID branchId,
    String fullName,
    String email,
    String phone,
    StaffRole role,
    String licenseNumber,
    String specialty,
    String bio,
    boolean active,
    Instant createdAt
) {}
