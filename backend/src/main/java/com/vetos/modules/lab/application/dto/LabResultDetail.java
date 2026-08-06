package com.vetos.modules.lab.application.dto;

import com.vetos.modules.lab.domain.LabResultStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LabResultDetail(
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
    List<LabResultItemDetail> items,
    List<LabResultFileMeta> files
) {}
