package com.vetos.modules.lab.api.dto;

import com.vetos.modules.lab.domain.LabValueFlag;
import jakarta.validation.constraints.NotBlank;

public record LabResultItemRequest(@NotBlank String parameterName, @NotBlank String value, String unit, String referenceRange, LabValueFlag flag) {}
