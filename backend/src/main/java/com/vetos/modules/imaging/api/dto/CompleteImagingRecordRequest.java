package com.vetos.modules.imaging.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteImagingRecordRequest(@NotBlank String findings) {}
