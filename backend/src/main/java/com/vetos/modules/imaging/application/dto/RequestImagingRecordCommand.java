package com.vetos.modules.imaging.application.dto;

import com.vetos.modules.imaging.domain.ImagingModality;

import java.util.UUID;

public record RequestImagingRecordCommand(
    UUID tenantId, UUID patientId, UUID orderingStaffId, ImagingModality modality, String bodyRegion, String notes
) {}
