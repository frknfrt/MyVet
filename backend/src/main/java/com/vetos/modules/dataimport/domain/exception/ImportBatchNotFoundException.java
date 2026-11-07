package com.vetos.modules.dataimport.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

/** GlobalExceptionHandler: 404. */
public class ImportBatchNotFoundException extends DomainException {
    public ImportBatchNotFoundException(UUID batchId) {
        super("IMPORT_BATCH_NOT_FOUND", "Aktarım bulunamadı: " + batchId);
    }
}
