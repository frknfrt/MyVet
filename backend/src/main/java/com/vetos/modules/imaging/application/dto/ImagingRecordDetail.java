package com.vetos.modules.imaging.application.dto;

import com.vetos.modules.imaging.domain.ImagingModality;
import com.vetos.modules.imaging.domain.ImagingRecordStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImagingRecordDetail(
    UUID id,
    UUID patientId,
    String patientName,
    UUID ownerId,
    String ownerFullName,
    ImagingModality modality,
    String bodyRegion,
    ImagingRecordStatus status,
    Instant requestedAt,
    Instant resultedAt,
    String orderingStaffName,
    String findings,
    String notes,
    List<ImagingRecordFileMeta> files
) {}
