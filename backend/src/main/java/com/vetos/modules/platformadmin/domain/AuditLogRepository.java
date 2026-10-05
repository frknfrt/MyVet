package com.vetos.modules.platformadmin.domain;

import java.util.List;

public interface AuditLogRepository {
    AuditLogEntry save(AuditLogEntry entry);
    List<AuditLogEntry> findTop200ByOrderByCreatedAtDesc();
}
