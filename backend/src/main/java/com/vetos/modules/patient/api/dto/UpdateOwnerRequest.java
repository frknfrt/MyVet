package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateOwnerRequest(@NotBlank String phone, String email, String address) {}
