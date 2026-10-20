package com.vetos.platform.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventView(
    UUID id,
    Instant occurredAt,
    String actorType,
    UUID actorId,
    String actorName,
    String actorRole,
    String impersonatedBy,
    String channel,
    String action,
    String actionLabel,
    String targetType,
    UUID targetId,
    String targetLabel,
    String summary,
    String ipAddress
) {
}
