package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;

import java.time.Instant;
import java.util.UUID;

public record TarbilSyncLogSummary(
    UUID id, UUID patientId, String patientName, TarbilSyncType syncType, TarbilSyncStatus status, Instant attemptedAt
) {}
