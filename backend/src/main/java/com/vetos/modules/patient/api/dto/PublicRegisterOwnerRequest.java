package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PublicRegisterOwnerRequest(
    @NotNull UUID tenantId,
    @NotBlank String fullName,
    @NotBlank String phone,
    String email
) {}
