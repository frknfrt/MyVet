package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSyncLogSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TarbilSyncLogResponse(
    UUID id, UUID patientId, String patientName, String vaccineName, LocalDate administeredDate,
    TarbilSyncStatus status, Instant queuedAt, Instant submittedAt,
    TarbilConfirmationMethod confirmationMethod, String tarbilReference, String dismissedReason
) {
    public static TarbilSyncLogResponse from(TarbilSyncLogSummary s) {
        return new TarbilSyncLogResponse(s.id(), s.patientId(), s.patientName(), s.vaccineName(), s.administeredDate(),
            s.status(), s.queuedAt(), s.submittedAt(), s.confirmationMethod(), s.tarbilReference(), s.dismissedReason());
    }
}
