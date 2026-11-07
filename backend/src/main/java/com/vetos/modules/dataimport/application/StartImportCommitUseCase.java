package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.exception.ImportBatchNotFoundException;
import com.vetos.modules.dataimport.domain.exception.ImportStateConflictException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Onizlenmis partiyi COMMITTING'e alir ve isciyi arka planda baslatir.
 * Durum degisikligi kilitli satirla kendi transaction'inda commit edilir; isci ANCAK ondan sonra tetiklenir
 * (aksi halde isci partiyi hala PREVIEWED gorebilirdi).
 */
@Service
@RequiredArgsConstructor
public class StartImportCommitUseCase {

    private final ImportBatchRepository batchRepository;
    private final ImportCommitWorker worker;
    private final ImportTransactions transactions;

    public ImportBatch execute(UUID tenantId, UUID batchId, UUID adminId, String adminEmail,
                               List<String> rejectedMatchSourceIds) {
        ImportBatch started = transactions.asRoot(() -> {
            ImportBatch batch = batchRepository.findByIdForUpdate(batchId)
                .filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ImportBatchNotFoundException(batchId));
            if (batch.getStatus() != ImportBatchStatus.PREVIEWED) {
                throw new ImportStateConflictException(
                    "Aktarım yalnızca önizleme durumundayken başlatılabilir (şu an: " + batch.getStatus() + ")");
            }
            if (!batch.getReport().canCommit()) {
                throw new ImportStateConflictException(
                    "Önizleme raporunda yapısal hata var ya da aktarılacak kayıt yok");
            }
            batch.startCommit(rejectedMatchSourceIds == null ? List.of() : rejectedMatchSourceIds);
            return batchRepository.save(batch);
        });
        worker.runAsync(batchId, adminId, adminEmail);
        return started;
    }
}
