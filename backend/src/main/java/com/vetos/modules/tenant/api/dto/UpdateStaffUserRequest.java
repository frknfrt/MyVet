package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.domain.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateStaffUserRequest(
    @NotBlank String fullName,
    String phone,
    @NotNull StaffRole role,
    String licenseNumber,
    String specialty,
    String bio
) {}
