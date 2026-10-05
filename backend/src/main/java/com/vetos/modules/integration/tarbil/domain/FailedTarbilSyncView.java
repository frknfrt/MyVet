package com.vetos.modules.integration.tarbil.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform admin Sistem Sagligi paneli icin -- eklentiden uzun suredir gonderilmeyen (takilmis) bir TARBIL
 * aktariminin kiraci ve hasta adiyla ozeti. Eklenti modelinde sunucu TARBIL'e kendisi gondermez; "basarisiz senkron"
 * yoktur. Alan adlari canlidaki platform admin arayuzuyle uyum icin korundu: syncLogId = aktarim kimligi,
 * syncType = belge turu (VACCINATION | PRESCRIPTION | STOCK_RECEIPT), attemptedAt = kuyruga girdigi an.
 */
public record FailedTarbilSyncView(
    UUID syncLogId, UUID tenantId, String tenantName, UUID patientId, String patientName,
    String syncType, String failureReason, int attemptCount, Instant attemptedAt
) {}
