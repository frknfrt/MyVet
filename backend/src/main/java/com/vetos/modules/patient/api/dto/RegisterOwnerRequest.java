package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterOwnerRequest(
    @NotBlank String fullName,
    @NotBlank String phone,
    String email,
    String address,
    boolean marketingConsent
) {}
