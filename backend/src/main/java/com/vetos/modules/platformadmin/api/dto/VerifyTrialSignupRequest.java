package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyTrialSignupRequest(@NotNull UUID requestId, @NotBlank @Size(max = 12) String code) {}
