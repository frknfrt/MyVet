package com.vetos.modules.imaging.api.dto;

import com.vetos.modules.imaging.domain.ImagingModality;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RequestImagingRecordRequest(@NotNull UUID patientId, @NotNull ImagingModality modality, String bodyRegion, String notes) {}
