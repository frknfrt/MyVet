package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlatformTenantRequest(
    @NotBlank String tenantName,
    String taxNumber,
    @NotBlank String branchName,
    @NotBlank String adminFullName,
    @NotBlank @Email String adminEmail,
    @NotBlank @Size(min = 8) String adminPassword
) {}
