package com.vetos.modules.lab.api.dto;

import com.vetos.modules.lab.application.dto.LabResultDetail;
import com.vetos.modules.lab.application.dto.LabResultFileMeta;
import com.vetos.modules.lab.application.dto.LabResultItemDetail;
import com.vetos.modules.lab.domain.LabResultStatus;
import com.vetos.modules.lab.domain.LabValueFlag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LabResultDetailResponse(
    UUID id,
    UUID patientId,
    String patientName,
    UUID ownerId,
    String ownerFullName,
    String testName,
    LabResultStatus status,
    Instant requestedAt,
    Instant resultedAt,
    String orderingStaffName,
    String resultSummary,
    String notes,
    List<ItemResponse> items,
    List<FileResponse> files
) {
    public record ItemResponse(UUID id, String parameterName, String value, String unit, String referenceRange, LabValueFlag flag) {}
    public record FileResponse(UUID id, String fileName, String contentType, long fileSize, Instant uploadedAt) {}

    public static LabResultDetailResponse from(LabResultDetail d) {
        return new LabResultDetailResponse(
            d.id(), d.patientId(), d.patientName(), d.ownerId(), d.ownerFullName(),
            d.testName(), d.status(), d.requestedAt(), d.resultedAt(), d.orderingStaffName(),
            d.resultSummary(), d.notes(),
            d.items().stream().map(LabResultDetailResponse::toItem).toList(),
            d.files().stream().map(LabResultDetailResponse::toFile).toList()
        );
    }

    private static ItemResponse toItem(LabResultItemDetail i) {
        return new ItemResponse(i.id(), i.parameterName(), i.value(), i.unit(), i.referenceRange(), i.flag());
    }

    private static FileResponse toFile(LabResultFileMeta f) {
        return new FileResponse(f.id(), f.fileName(), f.contentType(), f.fileSize(), f.uploadedAt());
    }
}
