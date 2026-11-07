package com.vetos.modules.consentform.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidSignedFormRequest(@NotBlank String reason) {}
