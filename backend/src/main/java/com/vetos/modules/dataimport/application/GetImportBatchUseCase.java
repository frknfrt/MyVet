package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.exception.ImportBatchNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetImportBatchUseCase {

    private final ImportBatchRepository batchRepository;

    /** Parti baska bir klinige aitse "bulunamadi" gibi davranir (sizinti yok). */
    public ImportBatch execute(UUID tenantId, UUID batchId) {
        return batchRepository.findById(batchId)
            .filter(b -> b.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ImportBatchNotFoundException(batchId));
    }
}
