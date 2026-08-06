package com.vetos.modules.lab.api.dto;

import com.vetos.modules.lab.application.dto.LabResultSummary;
import com.vetos.modules.lab.domain.LabResultStatus;

import java.time.Instant;
import java.util.UUID;

public record LabResultSummaryResponse(
    UUID id,
    UUID patientId,
    String patientName,
    UUID ownerId,
    String ownerFullName,
    String testName,
    LabResultStatus status,
    Instant requestedAt,
    Instant resultedAt,
    String orderingStaffName
) {
    public static LabResultSummaryResponse from(LabResultSummary s) {
        return new LabResultSummaryResponse(
            s.id(), s.patientId(), s.patientName(), s.ownerId(), s.ownerFullName(),
            s.testName(), s.status(), s.requestedAt(), s.resultedAt(), s.orderingStaffName()
        );
    }
}
