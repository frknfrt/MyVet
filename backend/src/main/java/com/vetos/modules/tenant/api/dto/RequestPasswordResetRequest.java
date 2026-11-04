package com.vetos.modules.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestPasswordResetRequest(@NotBlank String email) {}
