package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarkSubmittedRequest(@NotNull TarbilConfirmationMethod method, @Size(max = 100) String tarbilReference) {}
