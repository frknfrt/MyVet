package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.application.dto.AiUsageByTenant;

import java.time.Instant;
import java.util.UUID;

public record AiUsageByTenantResponse(
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
) {
    public static AiUsageByTenantResponse from(AiUsageByTenant u) {
        return new AiUsageByTenantResponse(
            u.tenantId(), u.tenantName(), u.totalJobs(), u.diagnosisJobs(), u.treatmentJobs(),
            u.acceptedAsIs(), u.acceptedWithEdits(), u.rejected(), u.noDecisionYet(),
            u.accurateFeedback(), u.inaccurateFeedback(), u.lastUsedAt()
        );
    }
}
