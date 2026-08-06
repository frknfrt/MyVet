package com.vetos.modules.lab.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CompleteLabResultRequest(@NotBlank String resultSummary, @Valid List<LabResultItemRequest> items) {}
