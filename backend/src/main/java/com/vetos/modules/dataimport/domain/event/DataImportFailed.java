package com.vetos.modules.dataimport.domain.event;

import java.util.UUID;

public record DataImportFailed(UUID batchId, UUID tenantId, UUID adminId, String adminEmail, String details) {
}
