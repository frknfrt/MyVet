package com.vetos.modules.integration.tarbil.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ExtensionTokenSummary(
    UUID id, UUID staffUserId, String staffName, String label, Instant pairedAt, Instant lastUsedAt, Instant revokedAt
) {}
