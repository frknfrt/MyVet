package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.domain.StaffSummary;

import java.util.UUID;

public record StaffSummaryResponse(UUID id, String fullName, String role) {
    public static StaffSummaryResponse from(StaffSummary summary) {
        return new StaffSummaryResponse(summary.id(), summary.fullName(), summary.role().name());
    }
}
