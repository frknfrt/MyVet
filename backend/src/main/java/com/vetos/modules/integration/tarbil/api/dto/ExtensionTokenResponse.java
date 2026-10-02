package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionTokenSummary;

import java.time.Instant;
import java.util.UUID;

public record ExtensionTokenResponse(UUID id, UUID staffUserId, String staffName, String label,
                                     Instant pairedAt, Instant lastUsedAt, Instant revokedAt) {
    public static ExtensionTokenResponse from(ExtensionTokenSummary s) {
        return new ExtensionTokenResponse(s.id(), s.staffUserId(), s.staffName(), s.label(), s.pairedAt(), s.lastUsedAt(), s.revokedAt());
    }
}
