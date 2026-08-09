package com.vetos.modules.tenant.application.dto;

import com.vetos.modules.tenant.domain.StaffRole;

import java.util.UUID;

public record UpdateStaffUserCommand(
    UUID staffUserId,
    UUID actingStaffUserId,
    String fullName,
    String phone,
    StaffRole role,
    String licenseNumber,
    String specialty,
    String bio
) {}
