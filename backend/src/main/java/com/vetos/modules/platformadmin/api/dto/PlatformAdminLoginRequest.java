package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PlatformAdminLoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}
