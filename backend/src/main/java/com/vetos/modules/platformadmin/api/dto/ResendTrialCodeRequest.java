package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ResendTrialCodeRequest(@NotNull UUID requestId) {}
