package com.vetos.modules.imaging.api.dto;

import com.vetos.modules.imaging.application.dto.ImagingRecordSummary;
import com.vetos.modules.imaging.domain.ImagingModality;
import com.vetos.modules.imaging.domain.ImagingRecordStatus;

import java.time.Instant;
import java.util.UUID;

public record ImagingRecordSummaryResponse(
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
    String orderingStaffName
) {
    public static ImagingRecordSummaryResponse from(ImagingRecordSummary s) {
        return new ImagingRecordSummaryResponse(
            s.id(), s.patientId(), s.patientName(), s.ownerId(), s.ownerFullName(),
            s.modality(), s.bodyRegion(), s.status(), s.requestedAt(), s.resultedAt(), s.orderingStaffName()
        );
    }
}
