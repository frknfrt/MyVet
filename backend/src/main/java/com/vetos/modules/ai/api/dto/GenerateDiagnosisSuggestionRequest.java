package com.vetos.modules.ai.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GenerateDiagnosisSuggestionRequest(@NotNull UUID encounterId) {}
