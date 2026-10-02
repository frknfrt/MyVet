package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TarbilSyncLogSummary(
    UUID id, UUID patientId, String patientName, String vaccineName, LocalDate administeredDate,
    TarbilSyncStatus status, Instant queuedAt, Instant submittedAt,
    TarbilConfirmationMethod confirmationMethod, String tarbilReference, String dismissedReason
) {}
