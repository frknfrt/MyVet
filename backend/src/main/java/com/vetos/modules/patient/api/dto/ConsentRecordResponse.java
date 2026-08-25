package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.ConsentRecordSummary;
import com.vetos.modules.patient.domain.ConsentType;

import java.time.Instant;
import java.util.UUID;

public record ConsentRecordResponse(
    UUID id,
    ConsentType consentType,
    boolean granted,
    String ipAddress,
    Instant grantedAt,
    Instant revokedAt
) {
    public static ConsentRecordResponse from(ConsentRecordSummary summary) {
        return new ConsentRecordResponse(
            summary.id(), summary.consentType(), summary.granted(), summary.ipAddress(),
            summary.grantedAt(), summary.revokedAt()
        );
    }
}
