package com.vetos.modules.dataimport.domain.event;

import java.util.UUID;

public record DataImportRolledBack(UUID batchId, UUID tenantId, UUID adminId, String adminEmail, String details) {
}
