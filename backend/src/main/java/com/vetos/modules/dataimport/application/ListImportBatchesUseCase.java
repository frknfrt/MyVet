package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListImportBatchesUseCase {

    private final ImportBatchRepository batchRepository;

    public List<ImportBatch> execute(UUID tenantId) {
        return batchRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
