package com.vetos.modules.imaging.api.dto;

import com.vetos.modules.imaging.application.dto.ImagingRecordDetail;
import com.vetos.modules.imaging.application.dto.ImagingRecordFileMeta;
import com.vetos.modules.imaging.domain.ImagingModality;
import com.vetos.modules.imaging.domain.ImagingRecordStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImagingRecordDetailResponse(
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
    List<FileResponse> files
) {
    public record FileResponse(UUID id, String fileName, String contentType, long fileSize, Instant uploadedAt) {}

    public static ImagingRecordDetailResponse from(ImagingRecordDetail d) {
        return new ImagingRecordDetailResponse(
            d.id(), d.patientId(), d.patientName(), d.ownerId(), d.ownerFullName(),
            d.modality(), d.bodyRegion(), d.status(), d.requestedAt(), d.resultedAt(), d.orderingStaffName(),
            d.findings(), d.notes(),
            d.files().stream().map(ImagingRecordDetailResponse::toFile).toList()
        );
    }

    private static FileResponse toFile(ImagingRecordFileMeta f) {
        return new FileResponse(f.id(), f.fileName(), f.contentType(), f.fileSize(), f.uploadedAt());
    }
}
