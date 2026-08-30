package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InitiateSignupCheckoutRequest(
    @NotBlank String clinicName,
    @NotBlank String adminFullName,
    @NotBlank @Email String adminEmail,
    String phone,
    @NotBlank String planCode
) {}
