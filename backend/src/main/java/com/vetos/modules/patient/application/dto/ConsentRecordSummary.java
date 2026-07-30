package com.vetos.modules.patient.application.dto;

import com.vetos.modules.patient.domain.ConsentType;

import java.time.Instant;
import java.util.UUID;

public record ConsentRecordSummary(
    UUID id,
    ConsentType consentType,
    boolean granted,
    Instant grantedAt,
    Instant revokedAt
) {}
