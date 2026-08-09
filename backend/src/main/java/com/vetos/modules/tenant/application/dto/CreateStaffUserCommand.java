package com.vetos.modules.tenant.application.dto;

import com.vetos.modules.tenant.domain.StaffRole;

import java.util.UUID;

public record CreateStaffUserCommand(
    UUID branchId,
    String fullName,
    String email,
    String password,
    StaffRole role,
    String phone,
    String licenseNumber,
    String specialty,
    String bio
) {}
