package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.exception.ImportBatchNotFoundException;
import com.vetos.modules.dataimport.domain.exception.ImportStateConflictException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscardImportUseCase {

    private final ImportBatchRepository batchRepository;

    @Transactional
    public void execute(UUID batchId) {
        // Kilitli okuma: ayni anda baslatilan aktarimin COMMITTING durumunun ustune DISCARDED yazilmasin.
        ImportBatch batch = batchRepository.findByIdForUpdate(batchId)
            .orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        try {
            batch.discard();
        } catch (IllegalStateException e) {
            throw new ImportStateConflictException(e.getMessage());
        }
        batchRepository.save(batch);
    }
}
