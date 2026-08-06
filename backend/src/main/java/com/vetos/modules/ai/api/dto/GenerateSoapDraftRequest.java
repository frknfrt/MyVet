package com.vetos.modules.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateSoapDraftRequest(@NotBlank String transcript) {}
