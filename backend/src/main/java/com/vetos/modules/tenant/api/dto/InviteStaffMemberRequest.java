package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.domain.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteStaffMemberRequest(
    @NotNull UUID branchId,
    @NotBlank String fullName,
    @NotBlank @Email String email,
    @NotNull StaffRole role
) {}
