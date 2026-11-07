package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Arayuzde "Geri al" gosterilsin mi: RollbackImportUseCase'in kabul ettigi kaynak durumlarla ayni kural --
 * COMMITTED, ya da hala linki duran (otomatik temizligi basarisiz olmus) FAILED.
 */
@Component
@RequiredArgsConstructor
public class ImportRollbackEligibility {

    private final ImportRecordLinkRepository linkRepository;

    public Set<UUID> rollbackableIds(UUID tenantId, Collection<ImportBatch> batches) {
        Set<UUID> result = new HashSet<>();
        List<UUID> failed = batches.stream()
            .filter(b -> b.getStatus() == ImportBatchStatus.FAILED).map(ImportBatch::getId).toList();
        batches.stream().filter(b -> b.getStatus() == ImportBatchStatus.COMMITTED)
            .forEach(b -> result.add(b.getId()));
        if (!failed.isEmpty()) {
            result.addAll(linkRepository.batchIdsWithLinks(tenantId, failed));
        }
        return result;
    }

    public boolean isRollbackable(ImportBatch batch) {
        return rollbackableIds(batch.getTenantId(), List.of(batch)).contains(batch.getId());
    }
}
