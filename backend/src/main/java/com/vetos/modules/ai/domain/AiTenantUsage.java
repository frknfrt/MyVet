package com.vetos.modules.ai.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform admin'in "AI Kullanım Paneli" icin tek bir kiracinin AI
 * kullanim/karar/dogruluk ozeti -- AiJob (iş hacmi) ve AiJobDecision
 * (hekim karari + dogruluk geri bildirimi) uzerinden hesaplanir.
 * noDecisionYet, hekimin oneriyi henuz kabul/red etmedigi (karar
 * kaydi hic olusmamis) is sayisidir.
 */
public record AiTenantUsage(
    UUID tenantId,
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
