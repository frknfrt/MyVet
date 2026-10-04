package com.vetos.modules.platformadmin.application.dto;

import java.time.Instant;
import java.util.UUID;

public record AiUsageByTenant(
    UUID tenantId,
    String tenantName,
    long totalJobs,
    long diagnosisJobs,
    long treatmentJobs,
    long acceptedAsIs,
    long acceptedWithEdits,
    long rejected,
    long noDecisionYet,
    long accurateFeedback,
    long inaccurateFeedback,
    Instant lastUsedAt
) {}
