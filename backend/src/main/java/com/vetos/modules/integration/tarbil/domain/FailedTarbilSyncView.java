package com.vetos.modules.integration.tarbil.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform admin Sistem Sagligi paneli icin -- basarisiz bir TARBIL
 * senkronunun kiraci ve hasta adiyla birlikte ozeti. bkz. TarbilHealthPort.
 * syncLogId, manuel "Tekrar Dene" aksiyonu icin gerekli (bkz. TarbilAdminPort).
 */
public record FailedTarbilSyncView(
    UUID syncLogId, UUID tenantId, String tenantName, UUID patientId, String patientName,
    TarbilSyncType syncType, String failureReason, int attemptCount, Instant attemptedAt
) {}
