package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.AuditLogEntry;

import java.time.Instant;
import java.util.UUID;

public record AuditLogEntryResponse(
    UUID id,
    UUID platformAdminId,
    String platformAdminEmail,
    String action,
    String targetType,
    UUID targetId,
    String details,
    Instant createdAt
) {
    public static AuditLogEntryResponse from(AuditLogEntry entry) {
        return new AuditLogEntryResponse(
            entry.getId(),
            entry.getPlatformAdminId(),
            entry.getPlatformAdminEmail(),
            entry.getAction(),
            entry.getTargetType(),
            entry.getTargetId(),
            entry.getDetails(),
            entry.getCreatedAt()
        );
    }
}
