package com.vetos.modules.lab.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RequestLabResultRequest(@NotNull UUID patientId, @NotBlank String testName, String notes) {}
