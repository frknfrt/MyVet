package com.vetos.modules.dataimport.api.dto;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.ImportReport;
import java.time.Instant;
import java.util.UUID;

/**
 * Kaynak dosya baytlari bilerek YOK. {@code rollbackable}: "Geri al" sunulabilir mi (COMMITTED ya da linki duran FAILED).
 */
public record ImportBatchResponse(
    UUID id, UUID tenantId, String sourceSystem, String fileName, ImportBatchStatus status,
    ImportReport report, int totalRows, int processedRows, String errorMessage,
    Instant createdAt, Instant committedAt, Instant rolledBackAt, boolean rollbackable
) {
    public static ImportBatchResponse from(ImportBatch b, boolean rollbackable) {
        return new ImportBatchResponse(b.getId(), b.getTenantId(), b.getSourceSystem(), b.getFileName(),
            b.getStatus(), b.getReport(), b.getTotalRows(), b.getProcessedRows(), b.getErrorMessage(),
            b.getCreatedAt(), b.getCommittedAt(), b.getRolledBackAt(), rollbackable);
    }
}
