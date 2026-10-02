package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PairRequest(@NotBlank @Size(max = 20) String code, @Size(max = 60) String label) {}
