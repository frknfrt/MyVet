package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.domain.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateStaffUserRequest(
    @NotNull UUID branchId,
    @NotBlank String fullName,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password,
    @NotNull StaffRole role,
    String phone,
    String licenseNumber,
    String specialty,
    String bio
) {}
