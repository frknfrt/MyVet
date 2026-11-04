package com.vetos.modules.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPasswordResetRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, message = "Yeni şifre en az 8 karakter olmalıdır") String newPassword
) {}
